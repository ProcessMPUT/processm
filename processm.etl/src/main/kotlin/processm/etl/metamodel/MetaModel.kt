package processm.etl.metamodel

import com.google.common.collect.Lists
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.sql.batchInsert
import org.jetbrains.exposed.sql.insertAndGetId
import org.jetbrains.exposed.sql.statements.BatchInsertStatement
import org.jetbrains.exposed.sql.transactions.transaction
import org.jgrapht.Graph
import org.postgresql.util.PGobject
import processm.core.Brand
import processm.core.helpers.mapToArray
import processm.core.helpers.toUUID
import processm.core.log.*
import processm.core.log.attribute.Attribute
import processm.core.log.attribute.mutableAttributeMapOf
import processm.core.logging.loggedScope
import processm.core.logging.logger
import processm.core.persistence.connection.DBCache
import processm.core.querylanguage.Query
import processm.dbmodels.models.AttributesNames
import processm.dbmodels.models.Classes
import processm.dbmodels.models.DataModels
import processm.dbmodels.models.Relationships
import processm.etl.discovery.DatabaseExplorer
import processm.etl.tracker.DatabaseChangeApplier
import processm.etl.tracker.DatabaseChangeApplier.DatabaseChangeEvent
import java.sql.Connection
import java.sql.JDBCType
import java.sql.PreparedStatement
import java.sql.ResultSet
import java.time.Instant
import java.util.*

class MetaModel(
    private val dataStoreDBName: String,
    private val metaModelReader: MetaModelReader,
    private val metaModelAppender: MetaModelAppender,
    private val etlProcessProvider: ETLProcessProvider
) : DatabaseChangeApplier {

    class SQLQueryBuilder {
        val queryBuilder = StringBuilder()
        val variables = ArrayList<Any>()

        val length: Int
            get() = queryBuilder.length

        fun <T> append(argument: T) {
            queryBuilder.append(argument)
        }

        fun deleteCharAt(index: Int) {
            queryBuilder.deleteCharAt(index)
        }

        fun delete(start: Int, end: Int) {
            queryBuilder.delete(start, end)
        }

        fun <T : Any> bind(argument: T) {
            variables.add(argument)
        }
    }

    private inline fun buildSQLQuery(block: SQLQueryBuilder.() -> Unit): SQLQueryBuilder =
        SQLQueryBuilder().apply(block)

    private fun Connection.prepareStatement(query: SQLQueryBuilder): PreparedStatement {
        val stmt = prepareStatement(query.queryBuilder.toString())
        for ((i, v) in query.variables.withIndex()) {
            when (v) {
                is String -> stmt.setString(i + 1, v)
                is Int -> stmt.setInt(i + 1, v)
                is Array<*> -> stmt.setArray(i + 1, createArrayOf(JDBCType.VARCHAR.name, v))
                is UUID -> stmt.setString(i + 1, v.toString())
                else -> TODO("Unsupported type: ${v::class}")
            }
        }
        return stmt
    }

    private fun Connection.retrieveRelatedObjectsUpwards(
        logId: UUID,
        leafs: List<RemoteObjectID>,
        graph: Graph<EntityID<Int>, ETLProcessStub.Arc>
    ): List<RemoteObjectID> {
        assert(leafs.isNotEmpty())
        var isValid = false
        val query = buildSQLQuery {
            append(
                """
                with L1 as (select distinct parent.event_id, parent.object_id, parent.class_id
                from objects_in_events parent, events_attributes r, objects_in_events child, events, traces, logs
                where child.event_id = r.event_id and r.string_value = parent.object_id and
                r.event_id = events.id and events.trace_id = traces.id and traces.log_id = logs.id and
                logs."identity:id"=?::uuid and (
            """
            )
            bind(logId)
            var nextTier = HashSet<EntityID<Int>>()
            for ((classId, sameClassLeafs) in leafs.groupBy { it.classId }) {
                val relations = graph.outgoingEdgesOf(classId)
                if (relations.isNotEmpty()) {
                    append("(child.class_id=? and child.object_id=ANY(?) and (")
                    bind(classId.value)
                    bind(sameClassLeafs.mapToArray { it.objectId })
                    for (r in relations) {
                        nextTier.add(r.targetClass)
                        append("(r.key=? and parent.class_id=?) or ")
                        bind("$DB_ATTR_NS:${r.attributeName}")
                        bind(r.targetClass.value)
                        isValid = true
                    }
                    delete(length - 3, length)
                    append(")) or ")
                }
            }
            if (!isValid)
                return emptyList()
            delete(length - 3, length)
            append("))")
            var tier = 2
            var currentTier = HashSet<EntityID<Int>>()
            while (nextTier.isNotEmpty()) {
                currentTier = nextTier.apply { nextTier = currentTier }
                assert(currentTier.isNotEmpty())
                val relations = currentTier.flatMapTo(HashSet()) { childClassId ->
                    graph.outgoingEdgesOf(childClassId)
                }
                if (relations.isEmpty())
                    break
                nextTier.clear()
                append(", L")
                append(tier)
                append(
                    """ as (select distinct parent.event_id, parent.object_id, parent.class_id from L"""
                )
                append(tier - 1)
                tier++
                append(
                    """ child, events_attributes r, objects_in_events parent, events, traces, logs
                        where child.event_id = r.event_id and r.string_value = parent.object_id and
                        r.event_id = events.id and events.trace_id = traces.id and traces.log_id = logs.id and
                        logs."identity:id"=?::uuid and ("""
                )
                bind(logId)
                for (r in relations) {
                    append("(r.key=? and child.class_id=? and parent.class_id=?) or ")
                    bind("$DB_ATTR_NS:${r.attributeName}")
                    bind(r.sourceClass.value)
                    bind(r.targetClass.value)
                    nextTier.add(r.targetClass)
                }
                delete(length - 3, length)
                append("))")
            }
            append(" select distinct object_id, class_id from (")
            for (t in 1 until tier - 1) {
                append("select * from L")
                append(t)
                append(" union ")
            }
            append("select * from L")
            append(tier - 1)
            append(") as x")
        }
        return prepareStatement(query).use { stmt ->
            return@use stmt.executeQuery().use { rs ->
                val result = ArrayList<RemoteObjectID>()
                while (rs.next()) {
                    result.add(RemoteObjectID(rs.getString(1), EntityID(rs.getInt(2), Classes)))
                }
                return@use result
            }
        }
    }

    private fun Connection.retrieveRelatedObjectsDownward(
        logId: UUID,
        roots: List<RemoteObjectID>,
        graph: Graph<EntityID<Int>, ETLProcessStub.Arc>,
        reject: Set<EntityID<Int>>
    ): List<RemoteObjectID> {
        assert(roots.isNotEmpty())
        var isValid = false
        val query = buildSQLQuery {
            append(
                """
                with L1 as (select distinct parent.object_id as parent_object_id, parent.class_id as parent_class_id,
                child.object_id, child.class_id
                from objects_in_events parent, events_attributes r, objects_in_events child, events, traces, logs
                where child.event_id = r.event_id and r.string_value = parent.object_id and
                r.event_id = events.id and events.trace_id = traces.id and traces.log_id = logs.id and
                logs."identity:id"=?::uuid and (
            """
            )
            bind(logId)
            var nextTier = HashSet<ETLProcessStub.Arc>()
            for ((classId, sameClassRoots) in roots.groupBy { it.classId }) {
                val relations = graph.incomingEdgesOf(classId).filter { it.sourceClass !in reject }
                if (relations.isNotEmpty()) {
                    nextTier.addAll(relations)
                    append("(parent.class_id=? and parent.object_id=ANY(?) and (")
                    bind(classId.value)
                    bind(sameClassRoots.mapToArray { it.objectId })
                    for (r in relations) {
                        append("(r.key=? and child.class_id=?) or ")
                        bind("$DB_ATTR_NS:${r.attributeName}")
                        bind(r.sourceClass.value)
                        isValid = true
                    }
                    delete(length - 3, length)
                    append(")) or ")
                }
            }
            delete(length - 3, length)
            append("))")
            var tier = 2
            var currentTier = HashSet<ETLProcessStub.Arc>()
            while (nextTier.isNotEmpty()) {
                currentTier = nextTier.apply { nextTier = currentTier }
                assert(currentTier.isNotEmpty())
                nextTier.clear()
                append(", L")
                append(tier)
                append(
                    """ as (select distinct parent.object_id as parent_object_id, parent.class_id as parent_class_id, 
                    child.object_id, child.class_id from L"""
                )
                append(tier - 1)
                tier++
                append(
                    """ parent, events_attributes r, objects_in_events child, events, traces, logs
                        where child.event_id = r.event_id and r.string_value = parent.object_id and
                        r.event_id = events.id and events.trace_id = traces.id and traces.log_id = logs.id and
                        logs."identity:id"=?::uuid and ("""
                )
                bind(logId)
                for (r in currentTier) {
                    append("(r.key=? and child.class_id=? and parent.class_id=?) or ")
                    bind("$DB_ATTR_NS:${r.attributeName}")
                    bind(r.sourceClass.value)
                    bind(r.targetClass.value)
                    nextTier.addAll(graph.incomingEdgesOf(r.sourceClass).filter { it.sourceClass !in reject })
                }
                delete(length - 3, length)
                append("))")
            }
            append(" select distinct object_id, class_id from (")
            for (t in 1 until tier - 1) {
                append("select * from L")
                append(t)
                append(" union ")
            }
            append("select * from L")
            append(tier - 1)
            append(") as x")
        }
        if (!isValid)
            return emptyList()
        return prepareStatement(query).use { stmt ->
            return@use stmt.executeQuery().use { rs ->
                val result = ArrayList<RemoteObjectID>()
                while (rs.next()) {
                    result.add(RemoteObjectID(rs.getString(1), EntityID(rs.getInt(2), Classes)))
                }
                return@use result
            }
        }
    }

    private fun ETLProcessStub.getRelatedObjects(
        start: RemoteObjectID,
        newAttributes: Map<String, String>
    ): Set<RemoteObjectID> {
        val result = HashSet<RemoteObjectID>()
        val graph = getRelevanceGraph()
        val leafs = mutableListOf(start)
        graph
            .outgoingEdgesOf(start.classId)
            .mapNotNullTo(leafs) { arc ->
                newAttributes[arc.attributeName]?.let { parentId -> RemoteObjectID(parentId, arc.targetClass) }
            }
        result.addAll(DBCache.get(dataStoreDBName).getConnection().use { connection ->
            val upward = connection.retrieveRelatedObjectsUpwards(
                processId,
                leafs,
                graph
            ) + leafs - setOf(start)
            if (upward.isEmpty())
                return@use upward
            //FIXME I am not sure this is right, as it ignores the distinction between identifying and converging classes
            val reject = mutableSetOf(start.classId)
            upward.mapTo(reject) { it.classId }
            val downward = connection.retrieveRelatedObjectsDownward(processId, upward, graph, reject)
            return@use upward + downward
        })
        return result - setOf(start)
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
        Lists.cartesianProduct(objects.groupBy { it.classId }.values.toList()).map { it.toSet() }

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
        val query = buildSQLQuery {
            append("Select ")
            append(projection)
            append(" from objects_in_trace,traces,logs")
            append(""" where objects_in_trace.trace_id=traces.id and traces.log_id=logs.id and logs."identity:id"=?::uuid and ARRAY[""")
            bind(logId)
            for (o in objects) {
                append("ROW(?, ?)::remote_object_identifier,")
                bind(o.classId.value)
                bind(o.objectId)
            }
            deleteCharAt(length - 1)
            append("] <@ objects_in_trace.objects")
        }
        prepareStatement(query).use { stmt ->
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
        val relevantTraces = retrievePastTraces(processId, setOf(remoteObject))
        val relatedObjects = getRelatedObjects(remoteObject, dbEvent.objectData)
        val (identifyingRelatedObjects, convergingRelatedObjects) = relatedObjects
            .partition { it.classId in identifyingClasses }
            .let { it.first.toMutableSet() to it.second.toSet() }  //TODO that is inefficient
        assert(remoteObject !in identifyingRelatedObjects)
        assert(remoteObject !in convergingRelatedObjects)
        val event = dbEvent.toXES(remoteObject.classId)
        if (relevantTraces.isNotEmpty()) {
            //This is an update to a preexisting object
            //It is not sufficient to rely on the event type in dbEvent, as what is a new object for the log is not
            //necessarily a new object for the replica
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
                    for (traceId in existingTraceIds) {
                        result.add(Trace(mutableAttributeMapOf(Attribute.IDENTITY_ID to traceId)))
                        result.add(event)
                    }
                } else {
                    for (ci in possibleCIs) {
                        val traceId = UUID.randomUUID()
                        result.add(Trace(mutableAttributeMapOf(Attribute.IDENTITY_ID to traceId)))
                        if (ci.isNotEmpty())
                            result.addAll(retrievePastEvents(processId, ci))
                        result.add(event)
                    }
                }
            } else {
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
                }
            }
        }
        return result
    }

    /**
     * Saves data from change events to meta model data storage.
     *
     * @param databaseChangeEvents List of database events to process.
     */
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
     * Returns a collection of traces built according to the provided business perspective definition. Each trace is a set
     * of IDs for the table [processm.dbmodels.models.ObjectVersions]
     *
     * @param businessPerspectiveDefinition An object containing business perspective details.
     */
    fun buildTracesForBusinessPerspective(businessPerspectiveDefinition: DAGBusinessPerspectiveDefinition): Sequence<Set<Int>> =
        error("This function is no longer relevant and is to be removed")

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