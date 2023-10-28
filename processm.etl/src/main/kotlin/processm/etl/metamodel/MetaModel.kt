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
import processm.core.helpers.mapToSet
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
import java.sql.ResultSet
import java.time.Instant
import java.util.*

class MetaModel(
    private val dataStoreDBName: String,
    private val metaModelReader: MetaModelReader,
    private val metaModelAppender: MetaModelAppender,
    private val etlProcessProvider: ETLProcessProvider
) : DatabaseChangeApplier {

    private fun Connection.retrieveRelatedObjectsUpwards(
        start: RemoteObjectID,
        path: List<ETLProcessStub.Arc>
    ): ArrayList<RemoteObjectID> {
        /**
         * select distinct r1.string_value as ekko, r2.string_value as eban
         * from objects_in_events as o1, events_attributes r1,
         * objects_in_events as o2, events_attributes r2
         * where
         * o1.objectId='d1' and o1.classId=12 and o1.event_id=r1.event_id and r1.key='db:ekko'
         * and o2.objectId=r1.string_value and o2.classId=11 and r2.event_id=o2.event_id and r2.key='db:eban';
         *
         * select distinct r0.string_value,r1.string_value from objects_in_events o0, events_attributes r0,objects_in_events o1, events_attributes r1 where
         * o0.object_id=? and o0.class_id=? and r0.key=? and r0.event_id=o0.event_id and o1.class_id=? and r1.key=? and r1.event_id=o1.event_id and o1.object_id=r0.string_value
         */
        //TODO log_id!!
        val query = buildString {
            append("select distinct ")
            for (i in path.indices) {
                append("r")
                append(i)
                append(".string_value,")
            }
            deleteCharAt(length - 1)
            append(" from ")
            for (i in path.indices) {
                append("objects_in_events o")
                append(i)
                append(", events_attributes r")
                append(i)
                append(",")
            }
            deleteCharAt(length - 1)
            append(" where o0.object_id=?")
            for (i in path.indices) {
                append(" and o")
                append(i)
                append(".class_id=?")
                append(" and r")
                append(i)
                append(".key=? and r")
                append(i)
                append(".event_id=o")
                append(i)
                append(".event_id")
            }
            for (i in 1 until path.size) {
                append(" and o")
                append(i)
                append(".object_id=r")
                append(i - 1)
                append(".string_value")
            }
        }
        return prepareStatement(query).use { stmt ->
            var ctr = 1
            stmt.setString(ctr++, start.objectId)
            stmt.setInt(ctr++, start.classId.value)
            for (i in path.indices)
                stmt.setString(ctr + 2 * i, "$DB_ATTR_NS:${path[i].attributeName}")
            for (i in 0 until path.size - 1)
                stmt.setInt(ctr + 2 * i + 1, path[i].targetClass.value)
            return@use stmt.executeQuery().use { rs ->
                val result = ArrayList<RemoteObjectID>()
                while (rs.next()) {
                    val objects =
                        path.mapIndexed { idx, arc -> RemoteObjectID(rs.getString(idx + 1), arc.targetClass) }
                    result.addAll(objects)
                }
                return@use result
            }
        }
    }

    //TODO I'd rather have a single query and a non-recursive function
    private fun Connection.retrieveRelatedObjectsDownward(
        roots: List<RemoteObjectID>,
        graph: Graph<EntityID<Int>, ETLProcessStub.Arc>,
        accept: Set<EntityID<Int>>
    ): List<RemoteObjectID> {
        //TODO log_id!!
        /**
         * select distinct o1.objectid from
         * objects_in_events as o1, events_attributes r1, objects_in_events as o2
         * where o1.event_id=r1.event_id and r1.key='db:eban' and r1.string_value=o2.objectid and o2.classid=10 and o1.classid=11;
         */
        assert(roots.isNotEmpty())
        assert(roots.all { it.classId == roots[0].classId })
        val relations = graph.incomingEdgesOf(roots[0].classId).filter { it.sourceClass in accept }
        if (relations.isNotEmpty()) {
            val query = buildString {
                append(
                    """select distinct child.object_id, child.class_id 
                |from objects_in_events parent, events_attributes r, objects_in_events child 
                |where child.event_id=r.event_id and r.string_value=parent.object_id and parent.class_id=? and parent.object_id=ANY(?) and (""".trimMargin()
                )
                for (r in relations)
                    append("(r.key=? and child.class_id=?) or ")
                delete(length - 3, length)
                append(")")
            }
            val relevant = prepareStatement(query).use { stmt ->
                var ctr = 1
                stmt.setInt(ctr++, roots[0].classId.value)
                stmt.setArray(ctr++, createArrayOf(JDBCType.VARCHAR.name, roots.mapToArray { it.objectId }))
                for (r in relations) {
                    stmt.setString(ctr++, "$DB_ATTR_NS:${r.attributeName}")
                    stmt.setInt(ctr++, r.sourceClass.value)
                }
                return@use stmt.executeQuery().use { rs ->
                    val result = ArrayList<RemoteObjectID>()
                    while (rs.next()) {
                        result.add(RemoteObjectID(rs.getString(1), EntityID(rs.getInt(2), Classes)))
                    }
                    return@use result
                }
            }
            if (relevant.isNotEmpty()) {
                for (sameClassObjects in relevant.groupBy { it.classId }.values)
                    relevant.addAll(retrieveRelatedObjectsDownward(sameClassObjects, graph, accept))
            }
            return relevant
        } else
            return emptyList()
    }

    fun ETLProcessStub.getRelatedObjects(
        start: RemoteObjectID,
        relevantClasses: Set<EntityID<Int>>,
        newAttributes: Map<String, String>
    ): Set<RemoteObjectID> {
        val result = HashSet<RemoteObjectID>()
        result.addAll(DBCache.get(dataStoreDBName).getConnection().use { connection ->
            val graph = getRelevanceGraph()
            var current = start.classId
            val path = ArrayList<ETLProcessStub.Arc>()
            while (true) {
                val arcs = graph.outgoingEdgesOf(current).filter { it.targetClass in relevantClasses }
                if (arcs.isEmpty())
                    break
                check(arcs.size == 1) { "Currently we support only tree-shaped business perspectives. Generalizing to arbitrary DAGs is TODO" }
                val arc = arcs.single()
                path.add(arc)
                current = arc.targetClass
            }
            val upward = if (path.isNotEmpty()) {
                val related = connection.retrieveRelatedObjectsUpwards(start, path)
                newAttributes[path[0].attributeName]?.let { parentId ->
                    val parent = RemoteObjectID(parentId, path[0].targetClass)
                    related.add(parent)
                    if (path.size > 1) {
                        val pathFromParent = path.subList(1, path.size)
                        related.addAll(connection.retrieveRelatedObjectsUpwards(parent, pathFromParent))
                    }
                }
                related
            } else emptyList()
            if (upward.isEmpty())
                return@use upward
            //FIXME I am not sure this is right, as it ignores the distinction between identifying and converging classes
            val accept = relevantClasses - upward.mapToSet { it.classId } - setOf(start.classId)
            //In upward, every object is of different class, so it is sufficient to visit them one by one
            val downward = upward.flatMap {
                connection.retrieveRelatedObjectsDownward(listOf(it), getRelevanceGraph(), accept)
            }
            return@use upward + downward
        })
        return result - setOf(start)
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
        val relatedObjects = getRelatedObjects(remoteObject, relevantClasses, dbEvent.objectData)
        val (identifyingRelatedObjects, convergingRelatedObjects) = relatedObjects
            .partition { it.classId in identifyingClasses }
            .let { it.first.toMutableSet() to it.second.toSet() }  //TODO that is inefficient
        assert(remoteObject !in identifyingRelatedObjects)
        assert(remoteObject !in convergingRelatedObjects)
        val event = dbEvent.toXES(remoteObject.classId)
        if (isKnown) {
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