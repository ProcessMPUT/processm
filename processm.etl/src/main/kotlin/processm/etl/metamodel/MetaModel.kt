package processm.etl.metamodel

import com.google.common.collect.Lists
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.sql.batchInsert
import org.jetbrains.exposed.sql.insertAndGetId
import org.jetbrains.exposed.sql.statements.BatchInsertStatement
import org.jetbrains.exposed.sql.statements.jdbc.JdbcConnectionImpl
import org.jetbrains.exposed.sql.transactions.transaction
import org.postgresql.jdbc.PgArray
import org.postgresql.util.PGobject
import processm.core.Brand
import processm.core.helpers.mapToSet
import processm.core.helpers.toUUID
import processm.core.log.*
import processm.core.log.Event
import processm.core.log.attribute.Attribute
import processm.core.log.attribute.mutableAttributeMapOf
import processm.core.logging.loggedScope
import processm.core.logging.logger
import processm.core.persistence.connection.DBCache
import processm.core.querylanguage.Query
import processm.dbmodels.models.*
import processm.etl.discovery.DatabaseExplorer
import processm.etl.tracker.DatabaseChangeApplier
import processm.etl.tracker.DatabaseChangeApplier.DatabaseChangeEvent
import java.lang.Class
import java.sql.Connection
import java.sql.ResultSet
import java.time.Instant
import java.util.*
import kotlin.collections.HashMap
import kotlin.collections.HashSet

class MetaModel(
    private val dataStoreDBName: String,
    private val metaModelReader: MetaModelReader,
    private val metaModelAppender: MetaModelAppender,
    private val etlProcessProvider: ETLProcessProvider
) : DatabaseChangeApplier {

    // TODO This is a placeholder!
    // TODO Eventually the graph should be transformed into a query and the problem of computing relevant objects should be moved to the db
    private fun Connection.retrieveKnownObjects(logId: UUID): HashMap<RemoteObjectID, HashMap<String, String>> {
        val query = """
            select event_id, key, string_value, int_value from 
            events_attributes,events,traces,logs where event_id=events.id and trace_id=traces.id and log_id=logs.id and 
            logs."identity:id"=?::uuid and 
            ((key like '$DB_ATTR_NS:%') or key='${Attribute.ORG_RESOURCE}' or key='$CLASSID_ATTR')
            order by "time:timestamp"
        """.trimIndent()
        return prepareStatement(query).use { stmt ->
            stmt.setString(1, logId.toString())
            stmt.executeQuery().use { rs ->
                val attributes = HashMap<Int, HashMap<String, String>>()
                val classIds = HashMap<Int, Int>()
                val objectIds = HashMap<Int, String>()
                while (rs.next()) {
                    val eventId = rs.getInt(1)
                    when (val key = rs.getString(2)) {
                        Attribute.ORG_RESOURCE -> objectIds[eventId] = rs.getString(3)
                        CLASSID_ATTR -> classIds[eventId] = rs.getInt(4)
                        else -> attributes.computeIfAbsent(eventId) { HashMap() }[key.substring(3, key.length)] =
                            rs.getString(3)
                    }
                }
                val result = HashMap<RemoteObjectID, HashMap<String, String>>()
                for (e in attributes) {
                    val key = RemoteObjectID(objectIds[e.key]!!, EntityID(classIds[e.key]!!, Classes))
                    result.computeIfAbsent(key) { HashMap() }.putAll(e.value)
                }
                return@use result
            }
        }
    }

    //TODO this is an abomination. It requries:
    //TODO a) careful rethinking what exactly should be returned by this function, i.e., what constitutes a related object
    //TODO b) moving it, at least to some extent, to the DB instead of first loading all the objects and filtering here
    //TODO ideally this will boil down to yet another SQL query generator
    fun ETLProcessStub.getRelatedObjects(
        start: RemoteObjectID,
        relevantClasses: Set<EntityID<Int>>,
        newAttributes: Map<String, String>
    ): Set<RemoteObjectID> {
        val knownObjects = DBCache.get(dataStoreDBName).getConnection().use { connection ->
            connection.retrieveKnownObjects(processId)
        }
        knownObjects.computeIfAbsent(start) { HashMap() }.putAll(newAttributes)
        println(knownObjects)
        val graph = getRelevanceGraph()
        val queue = ArrayDeque<RemoteObjectID>()
        queue.add(start)
        val result = mutableSetOf(start)
        // going up to find a path to the root - other objects of these classes are irrelevant, because either
        // they are of converging classes (and thus are defined by the identifying classes), or are necessary for the graph to remain connected
        // TODO revisit the former with DAGs, not trees
        while (queue.isNotEmpty()) {
            val obj = queue.poll()
            for (arc in graph.outgoingEdgesOf(obj.classId)) {
                if (arc.targetClass in relevantClasses) {
                    val targetId = knownObjects[obj]?.get(arc.attributeName)
                    checkNotNull(targetId)
                    val target = RemoteObjectID(targetId, arc.targetClass)
                    if (result.add(target)) {
                        queue.add(target)
                    }
                }
            }
        }
        assert(queue.isEmpty())
        val stillRelevantClasses = relevantClasses - result.mapToSet { it.classId }
        queue.addAll(result)
        while (queue.isNotEmpty()) {
            val obj = queue.poll()
            for (arc in graph.incomingEdgesOf(obj.classId)) {
                if (arc.sourceClass in stillRelevantClasses) {
                    for (e in knownObjects.entries) {
                        if (e.key.classId == arc.sourceClass && e.value[arc.attributeName] == obj.objectId) {
                            if (result.add(e.key)) {
                                queue.add(e.key)
                            }
                        }
                    }
                }
            }
        }
        result.remove(start)
        return result
    }

    private fun isKnownObject(logId: UUID, obj: RemoteObjectID): Boolean =
        DBCache.get(dataStoreDBName).getConnection().use { connection ->
            connection.queryObjectsInTrace(logId, listOf(obj), "1") { rs ->
                return@queryObjectsInTrace rs.next()
            }
        }

    private fun DatabaseChangeEvent.toXES(classId: EntityID<Int>) = Event(
        mutableAttributeMapOf(
            Attribute.IDENTITY_ID to UUID.randomUUID(),
            Attribute.ORG_RESOURCE to entityId,
            Attribute.CONCEPT_NAME to "${eventType.name} ${entityTable}",
            Attribute.LIFECYCLE_TRANSITION to eventType.name,
            "Activity" to eventType.name,
            Attribute.TIME_TIMESTAMP to (timestamp?.let(Instant::ofEpochMilli) ?: Instant.now()),
            CLASSID_ATTR to classId.value,
            KEY_ATTR to "${classId.value}_$entityId"
        ).apply {
            objectData.forEach { (k, v) -> set("$DB_ATTR_NS:$k", v) }
        })

    private fun retrievePastEvents(logId: UUID, objects: Collection<RemoteObjectID>): Sequence<Event> {
        assert(objects.isNotEmpty())
        val query = buildString {
            append("select e:* where l:identity:id=")
            append(logId)
            append(" and (")
            for (o in objects) {
                append("(e:")
                append(Attribute.ORG_RESOURCE)
                append("='")
                append(o.objectId)  //TODO binding variables? escaping?
                append("' and [e:$CLASSID_ATTR]='") //TODO I suspect a bug in the PQL implementation since '' here should not be needed.
                append(o.classId.value)
                append("') or ")
            }
            deleteRange(length - 3, length)
            append(")")
        }
        //TODO what about duplicates? are they possible?
        return DBXESInputStream(dataStoreDBName, Query(query)).filterIsInstance<Event>()
    }

    private fun identifyingRelatedObjectsToPossibleCaseIdentifiers(objects: Collection<RemoteObjectID>) =
        /*if (objects.isEmpty()) emptyList()
        else*/ Lists.cartesianProduct(objects.groupBy { it.classId }.values.toList()).map { it.toSet() }

//    private fun createOrUpdateTrace(identityId: UUID? = null, objects: Set<RemoteObjectID>): Trace =
//        Trace(mutableAttributeMapOf(Attribute.IDENTITY_ID to (identityId ?: UUID.randomUUID())).apply {
//            for (o in objects)
//                set("$INTERNAL_NS:${o.classId.value}", o.objectId)
//        })


    /**
     * Returns all the traces referring to all given objects (jointly - all objects in every trace).
     * A trace can relate to more objects.
     * Turns out PQL cannot do it, so raw SQL it is.
     */
    private fun retrievePastTraces(logId: UUID, objects: Collection<RemoteObjectID>): Set<UUID> {
        assert(objects.isNotEmpty())
        return DBCache.get(dataStoreDBName).getConnection().use { connection ->
            connection.queryObjectsInTrace(logId, objects, """traces."identity:id"""") { rs ->
                val result = HashSet<UUID>()
                while (rs.next()) {
                    rs.getString(1).toUUID()?.let { result.add(it) }
                }
                return@queryObjectsInTrace result
            }
        }
    }

    private fun <R> Connection.queryObjectsInTrace(
        logId: UUID,
        objects: Collection<RemoteObjectID>,
        projection: String,
        handler: (ResultSet) -> R
    ): R {
        val query = buildString {
            append("Select ")
            append(projection)
            append(" from objects_in_trace,traces,logs")
            append(""" where objects_in_trace.trace_id=traces.id and traces.log_id=logs.id and logs."identity:id"=?::uuid and ARRAY[""")
            for (o in objects)
                append("ROW(?, ?)::remote_object_identifier,")
            deleteCharAt(length - 1)
            append("] <@ objects_in_trace.objects")
        }
        println(query)
        prepareStatement(query).use { stmt ->
            var ctr = 1
            stmt.setString(ctr++, logId.toString())
            for (o in objects) {
                stmt.setInt(ctr++, o.classId.value)
                stmt.setString(ctr++, o.objectId)
            }
            return stmt.executeQuery().use(handler)
        }
    }


    /**
     * Returns all traces in the given log containing the given objects along with all the containted objects.
     */
    private fun retrievePastTracesWithObjects(
        logId: UUID,
        objects: Collection<RemoteObjectID>
    ): Map<UUID, Set<RemoteObjectID>> {
        assert(objects.isNotEmpty())
        assert(objects.isNotEmpty())
        return DBCache.get(dataStoreDBName).getConnection().use { connection ->
            connection.queryObjectsInTrace(logId, objects, """traces."identity:id", objects""") { rs ->
                val result = HashMap<UUID, Set<RemoteObjectID>>()
                while (rs.next()) {
                    val traceId = checkNotNull(rs.getString(1).toUUID())
                    val objectsOfTrace = HashSet<RemoteObjectID>()
                    result[traceId] = objectsOfTrace
                    (rs.getArray(2).array as Array<*>).forEach { o ->
                        //TODO Nie podoba mi sie to, moze mozna miec tablice?
                        o as PGobject
                        assert(o.type == "remote_object_identifier")
                        val v = checkNotNull(o.value)
                        val (classId, objectId) = v.substring(1, v.length - 1).split(',', limit = 2)
                        objectsOfTrace.add(RemoteObjectID(objectId, EntityID(classId.toInt(), Classes)))
                    }
                }
                return@queryObjectsInTrace result
            }
        }
    }

    /**
     * Trace (case identifier) = po jednym obiekcie z identifying classes
     * Events = wszystkie zdarzenia zwiazane z obiektami w case identifier + wszystkie zdarzenia wszystkich obiektów z nimi powiązanych z pozostałych klas
     *
     * Przychodzi event. Jezeli dotyczy znanego obiektu po prostu doklejamy go do wszystkich traces z tym obiektem.
     * Jeżeli dotyczy nowego obiektu:
     * 1. Ściągamy obiekty, z którymi jest powiązany
     * 2a. Jeżeli jest z identifying class i istnieje trace zawierający powiązane obiekty z identyfing classes, ale bez obiektu tej klasy -> doklejamy do tego trace
     * 2b. Jeżeli jest z identyfing class i nie da się go nigdzie dokleić -> zakładamy nowy trace
     * 2c. Jeżeli jest z converging class i istnieje trace pasujący do jego identifying classes -> doklejamy do tamtego trace
     * 2d. WPP -> czy to się może zdarzyć??
     * 3. Jeżeli założyliśmy nowy trace to populujemy adekwatnymi eventami wyciągniętymi z przeszłości
     */
    private fun ETLProcessStub.processEvent(dbEvent: DatabaseChangeEvent): List<XESComponent> {
        val result = ArrayList<XESComponent>()
        result.add(Log(mutableAttributeMapOf(Attribute.IDENTITY_ID to processId)))
        val remoteObject = RemoteObjectID(dbEvent.entityId, metaModelReader.getClassId(dbEvent.entityTable))
        val isKnown = isKnownObject(processId, remoteObject)
//        updateObject(remoteObject, dbEvent)
        val relatedObjects = getRelatedObjects(remoteObject, relevantClasses, dbEvent.objectData)
        val (identifyingRelatedObjects, convergingRelatedObjects) = relatedObjects
            .partition { it.classId in identifyingClasses }
            .let { it.first.toMutableSet() to it.second.toSet() }  //TODO that is inefficient
        assert(remoteObject !in identifyingRelatedObjects)
        assert(remoteObject !in convergingRelatedObjects)
        val event = dbEvent.toXES(remoteObject.classId)
        if (isKnown) {
            // a preexisting object - append the event to all preexisting traces related to that object
//            val relevantTraces = getTracesWithObject(remoteObject)
            val relevantTraces = retrievePastTraces(processId, setOf(remoteObject))
            for (traceId in relevantTraces) {
                result.add(Trace(mutableAttributeMapOf(Attribute.IDENTITY_ID to traceId)))
                result.add(event)
            }

        } else {
            // a new object
            if (remoteObject.classId in identifyingClasses) {
                //TODO the following line should be an assert I think
                identifyingRelatedObjects.removeIf { it.classId == remoteObject.classId }
                val possibleCIs = identifyingRelatedObjectsToPossibleCaseIdentifiers(identifyingRelatedObjects)
                println(possibleCIs)
//                val existingTraceIds = possibleCIs.mapNotNull { ci -> getTrace(ci)?.let { it to ci } }
                val existingTraceIds = ArrayList<UUID>()
                for (ci in possibleCIs) {
                    if (ci.isNotEmpty()) {
                        for ((traceId, objs) in retrievePastTracesWithObjects(processId, ci)) {
                            if (objs.filterTo(HashSet()) { it.classId in identifyingClasses } == ci)
                                existingTraceIds.add(traceId)
                        }
                    }
                }
                if (existingTraceIds.isNotEmpty()) {
                    //a new object suitable for completing a preexisting trace
//                    for ((traceId, ci) in existingTraceIds) {
//                        updateTrace(traceId, ci + setOf(remoteObject))
                    for (traceId in existingTraceIds) {
                        result.add(Trace(mutableAttributeMapOf(Attribute.IDENTITY_ID to traceId)))
                        result.add(event)
                    }
                } else {
                    for (ci in possibleCIs) {
                        val traceId = UUID.randomUUID()
//                        val traceId = createTrace(ci + setOf(remoteObject))
                        result.add(Trace(mutableAttributeMapOf(Attribute.IDENTITY_ID to traceId)))
                        if (ci.isNotEmpty())
                            result.addAll(retrievePastEvents(processId, ci))
                        result.add(event)
                    }
                }
            } else {
//                val existingTraceIds =
//                    identifyingRelatedObjectsToPossibleCaseIdentifiers(identifyingRelatedObjects).mapNotNull {
//                        getTrace(it)
//                    }
                val existingTraceIds =
                    identifyingRelatedObjectsToPossibleCaseIdentifiers(identifyingRelatedObjects).flatMap { ci ->
                        retrievePastTraces(processId, ci)
                    }
                if (existingTraceIds.isEmpty()) {
                    logger.warn("A new object from a converging class without a preexisting trace. This should be not possible unless the DB went haywire, e.g., constraints are disabled. Skipping this event.")
                }
                for (traceId in existingTraceIds) {
                    result.add(Trace(mutableAttributeMapOf(Attribute.IDENTITY_ID to traceId)))
                    result.add(event)
//                    addObjectToTrace(traceId, remoteObject)
                }
            }
        }
        return result
    }

    override fun applyChange(databaseChangeEvents: List<DatabaseChangeEvent>) =
        loggedScope { logger ->
            for (dbEvent in databaseChangeEvents) {
                AppendingDBXESOutputStream(DBCache.get(dataStoreDBName).getConnection()).use { output ->
                    transaction(DBCache.get(dataStoreDBName).database) {
                        for (etlProcess in etlProcessProvider.getProcessesForClass(dbEvent.entityTable)) {
                            val components = etlProcess.processEvent(dbEvent).toList()
                            for (c in components)
                                println("${c::class}: ${c.attributes.toList()}")
                            output.write(components.asSequence())
                        }
                    }
                }
            }
            logger.debug("Successfully handled ${databaseChangeEvents.count()} DB change events")
        }

    /**
     * Saves data from change events to meta model data storage.
     *
     * @param databaseChangeEvents List of database events to process.
     */
//    override fun applyChange(databaseChangeEvents: List<DatabaseChangeEvent>) =
//        transaction(DBCache.get(dataStoreDBName).database) {
//            loggedScope { logger ->
//                val now = Instant.now().toEpochMilli()
//                // Update timestamps of old object versions
//                databaseChangeEvents.filter { it.eventType == DatabaseChangeApplier.EventType.Update || it.eventType == DatabaseChangeApplier.EventType.Delete }
//                    .forEach { event ->
//                        metaModelAppender.updateObjectVersionEndTimestamp(
//                            event.entityId,
//                            metaModelReader.getClassId(event.entityTable),
//                            event.timestamp ?: now
//                        )
//                    }
//                // Insert the new object versions
//                databaseChangeEvents.forEach { event ->
//                    val classId = metaModelReader.getClassId(event.entityTable)
//                    val objectVersionFields = event.objectData.mapKeys { (attributeName) ->
//                        metaModelReader.getAttributeId(
//                            classId,
//                            attributeName
//                        )
//                    }
//
//                    metaModelAppender.addObjectVersion(
//                        event.entityId,
//                        classId,
//                        event.eventType,
//                        event.timestamp,
//                        objectVersionFields
//                    )
//                }
//
//
//                databaseChangeEvents.forEach { event ->
//                    for (etlProcess in etlProcessProvider.getProcessesForClass(event.entityTable)) {
//                        // DO NOT call output.close(), as it would commit transaction and close connection. Instead, we are
//                        // just attaching extra data to the exposed-managed database connection.
//                        val output = AppendingDBXESOutputStream((connection as JdbcConnectionImpl).connection)
//
//                        output.write(
//                            Log(
//                                mutableAttributeMapOf(
//                                    Attribute.LIFECYCLE_MODEL to "custom",
//                                    Attribute.IDENTITY_ID to etlProcess
//                                )
//                            )
//                        )
//                        //TODO identify trace correctly - this may be tricky
//                        // TODO znalezc najnowsze wersje obiektow powiazanych z event.entityId zgodnie z tym co jest w relations
//                        //TODO przywolac zdarzenia, ktore sa relevant do tego trace, a byly juz w przeszlosci
//                        //TODO chyba trzeba to wszystko opisac w issue i zaorac, w tym momencie szkoda czasu
//
////                        val traceIdQuery = getTraceId(event.entityTable, etlProcess)
////
////                        println(traceIdQuery)
////
////                        val traceIds =
////                            (connection as JdbcConnectionImpl).connection.prepareStatement(traceIdQuery).use { stmt ->
////                                stmt.setString(1, event.entityId)
////                                return@use stmt.executeQuery().use { rs ->
////                                    val nColumns = rs.metaData.columnCount
////                                    assert(nColumns % 2 == 0)
////                                    val result = ArrayList<Set<Pair<Int, Int>>>()
////                                    while (rs.next()) {
////                                        result.add(
////                                            (0 until nColumns / 2).mapToSet { rs.getInt(2 * it + 1) to rs.getInt(2 * it + 1 + 1) })
////                                    }
////                                    return@use result
////                                }
////                            }
////
////                        println("Event: ${event.entityId}/${event.entityTable}")
////                        println("Trace ID: $traceIds")
////
////                        val event = Event(
////                            mutableAttributeMapOf(
////                                Attribute.ORG_RESOURCE to event.entityId,
////                                Attribute.CONCEPT_NAME to "${event.eventType.name} ${event.entityTable}",
////                                Attribute.LIFECYCLE_TRANSITION to event.eventType.name,
////                                "Activity" to event.eventType.name,
////                                Attribute.TIME_TIMESTAMP to event.timestamp?.let(Instant::ofEpochMilli)
////                            ).apply {
////                                event.objectData.forEach { (k, v) -> set("$DB_ATTR_NS:$k", v) }
////                            }
////                        )
////
////                        for (traceId in traceIds) {
////                            output.write(Trace(mutableAttributeMapOf(
////                                Attribute.IDENTITY_ID to ci2traceId.computeIfAbsent(traceId) {
////                                    println("Creating new trace")
////                                    UUID.randomUUID()
////                                }
////                            )))
////                            output.write(event)
////                        }
////                        output.flush()
//                    }
//                }
//
//                logger.info("Successfully handled ${databaseChangeEvents.count()} DB change events")
//            }
//        }

//    private fun getTraceId(entityTable: String, etlProcessId: UUID): String {
//        val relations = etlProcessProvider.getRelationsForProcess(etlProcessId)
//        val classes = relations.fold(HashSet<Int>()) { list, relation ->
//            list.add(relation.first.value)
//            list.add(relation.second.value)
//            list
//        }
//        val entityClassId = metaModelReader.getClassId(entityTable)
//        // TODO wbrew artykulowi tu chyba powinny byc same identifying classes
////        val identifyingClasses = etlProcessProvider.getIdentifyingClasses(etlProcessId) //- setOf(entityClassId)
//        return buildString {
//            append("SELECT DISTINCT ")
//            classes.forEach {
//                append("c")
//                append(it)
//                append(".class_id,")
//                append("c")
//                append(it)
//                append(".object_id,")
//            }
//            deleteCharAt(length - 1)
//            append(" FROM ")
//            classes.forEach {
//                append("object_versions as c")
//                append(it)
//                append(",")
//            }
//            relations.forEach {
//                append("relations as r_")
//                append(it.first.value)
//                append("_")
//                append(it.second.value)
//                append(",")
//            }
//            deleteCharAt(length - 1)
//            append(" WHERE ")
//            classes.forEach {
//                append("c")
//                append(it)
//                append(".class_id = ")
//                append(it)
//                append(" AND ")
//            }
//            relations.forEach {
//                val r = "r_${it.first.value}_${it.second.value}"
//                append(r)
//                append(".source_object_version_id = ")
//                append("c")
//                append(it.first.value)
//                append(".id AND ")
//                append(r)
//                append(".target_object_version_id = ")
//                append("c")
//                append(it.second.value)
//                append(".id AND ")
//            }
//            append("c")
//            append(entityClassId)
//            //TODO sortowac po czasie nie po ID!!
//            append(".id = (SELECT MAX(c.id) FROM object_versions as c WHERE c.object_id = ? AND c.class_id = ${entityClassId.value})")
//        }
//    }

    /**
     * Returns a collection of traces built according to the provided business perspective definition. Each trace is a set
     * of IDs for the table [processm.dbmodels.models.ObjectVersions]
     *
     * @param businessPerspectiveDefinition An object containing business perspective details.
     */
    fun buildTracesForBusinessPerspective(businessPerspectiveDefinition: DAGBusinessPerspectiveDefinition): Sequence<Set<Int>> =
        sequence {
            val query = businessPerspectiveDefinition.generateSQLquery()
            DBCache.get(dataStoreDBName).getConnection().use { connection ->
                connection.prepareStatement(query).executeQuery().use { rs ->
                    while (rs.next()) {
                        yield((rs.getArray(1).array as Array<Int>).toSet())
                    }
                }
            }
        }

    companion object {
        val DB_ATTR_NS = "db"
        val INTERNAL_NS = "${Brand.mainDBInternalName}:internal"
        val CLASSID_ATTR = "$INTERNAL_NS:classId"
        val KEY_ATTR = "$INTERNAL_NS:key"

        val logger = logger()

        fun build(dataStoreDBName: String, metaModelName: String, databaseExplorer: DatabaseExplorer): EntityID<Int> {
            val classes = databaseExplorer.getClasses()
            val relationships = databaseExplorer.getRelationships()

            return transaction(DBCache.get(dataStoreDBName).database) {
                val dataModelId = DataModels.insertAndGetId {
                    it[name] = metaModelName
                }

                val classIds = Classes
                    .batchInsert(classes) {
                        this[Classes.name] = it.name
                        this[Classes.dataModelId] = dataModelId
                    }.associate {
                        it[Classes.name] to it[Classes.id]
                    }

                val referencingAttributeIds = BatchInsertStatement(AttributesNames).apply {
                    classes.forEach { metaModelClass ->
                        metaModelClass.attributes.forEach { attribute ->
                            classIds[metaModelClass.name]?.let { classId ->
                                addBatch()
                                this[AttributesNames.name] = attribute.name
                                this[AttributesNames.isReferencingAttribute] = attribute.isPartOfForeignKey
                                this[AttributesNames.type] = attribute.type
                                this[AttributesNames.classId] = classId
                            }
                        }
                    }
                    execute(this@transaction)
                }.resultedValues
                    .orEmpty()
                    .filter { it[AttributesNames.isReferencingAttribute] }
                    .associate {
                        (it[AttributesNames.classId] to it[AttributesNames.name]) to it[AttributesNames.id]
                    }

                BatchInsertStatement(Relationships).apply {
                    relationships.forEach { relationship ->
                        classIds[relationship.sourceClass.name]?.let { sourceClassId ->
                            classIds[relationship.targetClass.name]?.let { targetClassId ->
                                referencingAttributeIds[sourceClassId to relationship.sourceColumnName]?.let { referencingAttributeId ->
                                    addBatch()
                                    this[Relationships.name] = relationship.name
                                    this[Relationships.sourceClassId] = sourceClassId
                                    this[Relationships.targetClassId] = targetClassId
                                    this[Relationships.referencingAttributeNameId] = referencingAttributeId
                                }
                            }
                        }
                    }
                    execute(this@transaction)
                }

                return@transaction dataModelId
            }
        }
    }
}