package processm.etl.metamodel

import com.google.common.collect.Lists
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.select
import org.jgrapht.Graph
import org.jgrapht.graph.DefaultDirectedGraph
import processm.core.Brand
import processm.core.helpers.mapToArray
import processm.core.helpers.mapToSet
import processm.core.helpers.toUUID
import processm.core.log.*
import processm.core.log.attribute.Attribute
import processm.core.log.attribute.AttributeMap
import processm.core.log.attribute.MutableAttributeMap
import processm.core.log.attribute.mutableAttributeMapOf
import processm.core.logging.logger
import processm.core.persistence.connection.DBCache
import processm.dbmodels.models.*
import processm.etl.tracker.DatabaseChangeApplier
import java.sql.Connection
import java.sql.ResultSet
import java.time.Instant
import java.util.*

class AutomaticEtlProcessExecutor(
    val dataStoreDBName: String,
    val logId: UUID,
    val graph: Graph<EntityID<Int>, Arc>,
    val identifyingClasses: Set<EntityID<Int>>
) {

    init {
        val nRoots = graph.vertexSet().count { graph.outDegreeOf(it) == 0 }
        require(nRoots == 1) { "The graph must have a single distinguished root (i.e., a node with an out-degree equal to 0)" }
    }

    private val metaModelId =
        checkNotNull(Class.findById(graph.vertexSet().first())?.dataModel?.id) { "The DB is broken" }

    private val classIds = HashMap<String, EntityID<Int>>()
    private fun getClassId(className: String): EntityID<Int> =
        classIds.computeIfAbsent(className) {
            Classes.slice(Classes.id).select { (Classes.name eq className) and (Classes.dataModelId eq metaModelId) }
                .firstOrNull()
                ?.getOrNull(Classes.id)
                ?: throw NoSuchElementException("A class with the specified name $className does not exist")
        }

    private fun Connection.retrieveRelatedObjectsUpwards(leafs: List<RemoteObjectID>): List<RemoteObjectID> {
        assert(leafs.isNotEmpty())
        var isValid = false
        val query = buildSQLQuery {
            append(
                """
                with relevant as (select objects_in_events.*
                from objects_in_events, events, traces, logs
                where objects_in_events.event_id = events.id
                and events.trace_id = traces.id
                and traces.log_id = logs.id
                and logs."identity:id" = ?::uuid),
                """
            )
            bind(logId)
            append(
                """
                L1 as (select distinct parent.event_id, parent.object_id, parent.class_id
                from relevant parent, events_attributes r, relevant child
                where child.event_id = r.event_id and r.string_value = parent.object_id and (
            """
            )
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
                        bind("${DB_ATTR_NS}:${r.attributeName}")
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
                    """ child, events_attributes r, relevant parent
                        where child.event_id = r.event_id and r.string_value = parent.object_id and ("""
                )
                for (r in relations) {
                    append("(r.key=? and child.class_id=? and parent.class_id=?) or ")
                    bind("${DB_ATTR_NS}:${r.attributeName}")
                    bind(r.sourceClass.value)
                    bind(r.targetClass.value)
                    nextTier.add(r.targetClass)
                }
                delete(length - 3, length)
                append("))")
            }
            for (t in 1 until tier - 1) {
                append("select distinct object_id, class_id from L")
                append(t)
                append(" union ")
            }
            append("select distinct object_id, class_id from L")
            append(tier - 1)
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
        roots: Collection<RemoteObjectID>,
        reject: Set<EntityID<Int>>
    ): List<RemoteObjectID> {
        assert(roots.isNotEmpty())
        var isValid = false
        val query = buildSQLQuery {
            append(
                """
                with relevant as (select objects_in_events.*
                from objects_in_events, events, traces, logs
                where objects_in_events.event_id = events.id
                and events.trace_id = traces.id
                and traces.log_id = logs.id
                and logs."identity:id" = ?::uuid),
                """
            )
            bind(logId)
            append(
                """
                L1 as (select distinct 
                child.object_id, child.class_id
                from relevant parent, events_attributes r, relevant child
                where child.event_id = r.event_id and r.string_value = parent.object_id and (
            """
            )
            var nextTier = HashSet<Arc>()
            for ((classId, sameClassRoots) in roots.groupBy { it.classId }) {
                val relations = graph.incomingEdgesOf(classId).filter { it.sourceClass !in reject }
                if (relations.isNotEmpty()) {
                    append("(parent.class_id=? and parent.object_id=ANY(?) and (")
                    bind(classId.value)
                    bind(sameClassRoots.mapToArray { it.objectId })
                    for (r in relations) {
                        nextTier.addAll(graph.incomingEdgesOf(r.sourceClass).filter { it.sourceClass !in reject })
                        append("(r.key=? and child.class_id=?) or ")
                        bind("${DB_ATTR_NS}:${r.attributeName}")
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
            var currentTier = HashSet<Arc>()
            while (nextTier.isNotEmpty()) {
                currentTier = nextTier.apply { nextTier = currentTier }
                assert(currentTier.isNotEmpty())
                nextTier.clear()
                append(", L")
                append(tier)
                append(
                    """ as (select distinct 
                    child.object_id, child.class_id from L"""
                )
                append(tier - 1)
                tier++
                append(
                    """ parent, events_attributes r, relevant child
                        where child.event_id = r.event_id and r.string_value = parent.object_id and ("""
                )
                for (r in currentTier) {
                    append("(r.key=? and child.class_id=? and parent.class_id=?) or ")
                    bind("${DB_ATTR_NS}:${r.attributeName}")
                    bind(r.sourceClass.value)
                    bind(r.targetClass.value)
                    nextTier.addAll(graph.incomingEdgesOf(r.sourceClass).filter { it.sourceClass !in reject })
                }
                delete(length - 3, length)
                append("))")
            }
            for (t in 1 until tier - 1) {
                append("select distinct object_id, class_id from L")
                append(t)
                append(" union ")
            }
            append("select distinct object_id, class_id from L")
            append(tier - 1)
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

    private fun Connection.getRelatedObjects(
        start: RemoteObjectID,
        newAttributes: Map<String, String>
    ): Set<RemoteObjectID> {
        val leafs = mutableListOf(start)
        graph
            .outgoingEdgesOf(start.classId)
            .mapNotNullTo(leafs) { arc ->
                newAttributes[arc.attributeName]?.let { parentId -> RemoteObjectID(parentId, arc.targetClass) }
            }

        val upward = retrieveRelatedObjectsUpwards(leafs).toSet() + leafs - setOf(start)
        if (upward.isEmpty())
            return upward
        //FIXME I am not sure this is right, as it ignores the distinction between identifying and converging classes
        val reject = mutableSetOf(start.classId)
        upward.mapTo(reject) { it.classId }
        val downward = retrieveRelatedObjectsDownward(upward, reject)
        return upward + downward - setOf(start)
    }

    private fun DatabaseChangeApplier.DatabaseChangeEvent.toXES(classId: EntityID<Int>) = Event(
        mutableAttributeMapOf(
            Attribute.IDENTITY_ID to UUID.randomUUID(),
            Attribute.ORG_RESOURCE to entityId,
            Attribute.CONCEPT_NAME to "${eventType.name} ${entityTable}",
            Attribute.LIFECYCLE_TRANSITION to eventType.name,
            "Activity" to eventType.name,
            Attribute.TIME_TIMESTAMP to (timestamp?.let(Instant::ofEpochMilli) ?: Instant.now()),
            CLASSID_ATTR to classId.value
        ).apply {
            objectData.forEach { (k, v) -> set("${DB_ATTR_NS}:$k", v) }
        })

    //FIXME A copy from DBHierarchicalXESInputStream. I'd rather share it somehow
    private fun attributeFromRecord(record: ResultSet): Any {
        with(record) {
            val type = getString("type")!!
            assert(type.length >= 2)
            return when (type[0]) {
                's' -> getString("string_value")
                'f' -> getDouble("real_value")
                'i' -> when (type[1]) {
                    'n' -> getLong("int_value")
                    'd' -> getString("uuid_value").toUUID()!!
                    else -> throw IllegalStateException("Invalid attribute type ${getString("type")} in the database.")
                }

                'd' -> getTimestamp("date_value", gmtCalendar).toInstant()
                'b' -> getBoolean("bool_value")
                'l' -> AttributeMap.LIST_TAG
                else -> throw IllegalStateException("Invalid attribute type ${getString("type")} in the database.")
            }
        }
    }

    private fun Connection.retrievePastEvents(objects: Collection<RemoteObjectID>): List<Event> {
        assert(objects.isNotEmpty())
        // This seems to be more efficient than using PQL
        // TODO Consider moving the actual copying to the DB
        val query = buildSQLQuery {
            append(
                """select * from events_attributes where event_id in (
                select min(objects_in_events.event_id)
                from objects_in_events, events, traces, logs
                where objects_in_events.event_id = events.id
                and events.trace_id = traces.id
                and traces.log_id = logs.id
                and logs."identity:id"=?::uuid and ("""
            )
            bind(logId)
            for (o in objects) {
                append("(object_id=? and class_id=?) or")
                bind(o.objectId)
                bind(o.classId.value)
            }
            delete(length - 3, length)
            append(""")group by events."identity:id")""")
        }
        val events = HashMap<Int, MutableAttributeMap>()
        prepareStatement(query).use { stmt ->
            stmt.executeQuery().use { rs ->
                while (rs.next()) {
                    val eventId = rs.getInt("event_id")
                    val key = rs.getString("key")
                    val value = attributeFromRecord(rs)
                    events.computeIfAbsent(eventId) { MutableAttributeMap() }.putFlat(key, value)
                }
            }
        }
        return events.values.map { Event(it) }
    }

    private fun identifyingRelatedObjectsToPossibleCaseIdentifiers(objects: Collection<RemoteObjectID>) =
        Lists.cartesianProduct(objects.groupBy { it.classId }.values.toList()).map { it.toSet() }

    /**
     * Returns all the traces referring to all given objects (jointly - all objects in every trace).
     * A trace can relate to more objects.
     * Turns out PQL cannot do it, so raw SQL it is.
     */
    private fun Connection.retrievePastTraces(objects: Collection<RemoteObjectID>): Set<UUID> {
        assert(objects.isNotEmpty())
        return queryObjectsInTrace(objects, """traces."identity:id"""") { rs ->
            val result = HashSet<UUID>()
            while (rs.next()) {
                rs.getString(1).toUUID()?.let { result.add(it) }
            }
            return@queryObjectsInTrace result
        }
    }

    private fun <R> Connection.queryObjectsInTrace(
        objects: Collection<RemoteObjectID>,
        projection: String,
        handler: (ResultSet) -> R
    ): R {
        val query = buildSQLQuery {
            append("select ")
            append(projection)
            append(" from objects_in_traces,traces,logs")
            append(""" where objects_in_traces.trace_id=traces.id and traces.log_id=logs.id and logs."identity:id"=?::uuid and ARRAY[""")
            bind(logId)
            for (o in objects) {
                append("?::text,")
                bind("${o.classId.value}_${o.objectId}")
            }
            deleteCharAt(length - 1)
            append("] <@ objects_in_traces.objects")
        }
        prepareStatement(query).use { stmt ->
            return stmt.executeQuery().use(handler)
        }
    }


    /**
     * Returns all traces in the given log containing the given objects along with all the containted objects.
     */
    private fun Connection.retrievePastTracesWithObjects(objects: Collection<RemoteObjectID>): Map<UUID, Set<RemoteObjectID>> {
        assert(objects.isNotEmpty())
        assert(objects.isNotEmpty())
        return queryObjectsInTrace(objects, """traces."identity:id", objects""") { rs ->
            val result = HashMap<UUID, Set<RemoteObjectID>>()
            while (rs.next()) {
                val traceId = checkNotNull(rs.getString(1).toUUID())
                val objectsOfTrace = HashSet<RemoteObjectID>()
                result[traceId] = objectsOfTrace
                (rs.getArray(2).array as Array<*>).forEach { o ->
                    val v = checkNotNull(o as String?)
                    val (classId, objectId) = v.split('_', limit = 2)
                    objectsOfTrace.add(RemoteObjectID(objectId, EntityID(classId.toInt(), Classes)))
                }
            }
            return@queryObjectsInTrace result
        }
    }

    private fun Event.getObject(): RemoteObjectID? {
        val resource = orgResource ?: return null
        val classId = (attributes[CLASSID_ATTR] as Long?) ?: return null
        return RemoteObjectID(resource, EntityID(classId.toInt(), Classes))
    }

    private val queue = PersistentPostponedEventsQueue(logId)

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
    private fun Connection.processEvent(dbEvent: DatabaseChangeApplier.DatabaseChangeEvent): List<XESComponent> {
        val result = ArrayList<XESComponent>()
        val remoteObject = dbEvent.getObject()
        val relevantTraces = retrievePastTraces(setOf(remoteObject))
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
                        for ((traceId, objs) in retrievePastTracesWithObjects(ci)) {
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
                        if (ci.isNotEmpty()) {
                            val pastEvents = retrievePastEvents(ci).toList()
                            val objects = pastEvents.mapToSet { it.getObject() }
                            if (objects.containsAll(ci)) {
                                result.add(Trace(mutableAttributeMapOf(Attribute.IDENTITY_ID to UUID.randomUUID())))
                                result.addAll(pastEvents)
                                result.add(event)
                            }
                        } else {
                            result.add(Trace(mutableAttributeMapOf(Attribute.IDENTITY_ID to UUID.randomUUID())))
                            result.add(event)
                        }
                    }
                }
            } else {
                val existingTraceIds =
                    identifyingRelatedObjectsToPossibleCaseIdentifiers(identifyingRelatedObjects).flatMap { ci ->
                        retrievePastTraces(ci)
                    }
                for (traceId in existingTraceIds) {
                    result.add(Trace(mutableAttributeMapOf(Attribute.IDENTITY_ID to traceId)))
                    result.add(event)
                }
            }

        }
        return result
    }

    private fun List<XESComponent>.writeToDB() {
        assert(any { it is Event })
        AppendingDBXESOutputStream(DBCache.get(dataStoreDBName).getConnection()).use { output ->
            output.write(Log(mutableAttributeMapOf(Attribute.IDENTITY_ID to logId)))
            output.write(asSequence())
        }
    }

    private fun DatabaseChangeApplier.DatabaseChangeEvent.getObject() =
        RemoteObjectID(entityId, getClassId(entityTable))

    private fun processQueue(connection: Connection) {
        while (queue.isNotEmpty()) {
            var modified = false
            // Events related to the same object must be processed in order
            val unprocessedObjects = HashSet<RemoteObjectID>()
            for (event in queue) {
                val obj = event.objectId
                if (obj !in unprocessedObjects) {
                    val result = connection.processEvent(event.dbEvent)
                    if (result.any { it is Event }) {
                        logger.trace("Successfully processed {}", event)
                        result.writeToDB()
                        queue.remove(event)
                        modified = true
                    } else
                        unprocessedObjects.add(obj)
                }
            }
            if (!modified)
                break
        }
    }

    fun processEvent(dbEvent: DatabaseChangeApplier.DatabaseChangeEvent): Boolean {
        DBCache.get(dataStoreDBName).getConnection().use { connection ->
            logger.trace("For the first time {}", dbEvent)
            val objectOfEvent = dbEvent.getObject()
            // Process an event only if there are no unprocessed events related to this object
            val result =
                if (!queue.hasObject(objectOfEvent)) connection.processEvent(dbEvent)
                else emptyList()
            if (result.any { it is Event }) {
                result.writeToDB()
                processQueue(connection)
                return true
            } else {
                logger.trace("Postponing {}", dbEvent)
                queue.add(dbEvent, objectOfEvent)
                return false
            }
        }
    }


    companion object {
        private val gmtCalendar = Calendar.getInstance(TimeZone.getTimeZone("GMT"))

        val DB_ATTR_NS = "db"
        val INTERNAL_NS = "${Brand.mainDBInternalName}:internal"
        val CLASSID_ATTR = "$INTERNAL_NS:classId"

        val logger = logger()

        fun fromDB(
            dataStoreDBName: String,
            automaticEtlProcessId: UUID
        ): AutomaticEtlProcessExecutor {
            val relations = AutomaticEtlProcessRelation.wrapRows(AutomaticEtlProcessRelations
                .select { AutomaticEtlProcessRelations.automaticEtlProcessId eq automaticEtlProcessId })
            check(!relations.empty()) { "An automatic ETL process must refer to some relations " }
            val graph = DefaultDirectedGraph<EntityID<Int>, Arc>(Arc::class.java)
            for (relation in relations) {
                //TODO buggy and ugly. Add attributename to AutomaticEtlProcessRelation or replace AutomaticEtlProcessRelation with references to Relationships
                val attributeName = (Relationships innerJoin AttributesNames).slice(AttributesNames.name).select {
                    (Relationships.sourceClassId eq relation.sourceClassId) and (Relationships.targetClassId eq relation.targetClassId)
                }.single().let { it[AttributesNames.name] }
                val arc = Arc(relation.sourceClassId, attributeName, relation.targetClassId)
                graph.addVertex(relation.sourceClassId)
                graph.addVertex(relation.targetClassId)
                graph.addEdge(relation.sourceClassId, relation.targetClassId, arc)
            }
            return AutomaticEtlProcessExecutor(
                dataStoreDBName,
                automaticEtlProcessId,
                graph,
                graph.vertexSet()
            )

        }
    }
}