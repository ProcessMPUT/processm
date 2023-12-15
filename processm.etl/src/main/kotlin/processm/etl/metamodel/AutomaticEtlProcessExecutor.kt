package processm.etl.metamodel

import com.google.common.collect.Lists
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
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
import processm.core.log.attribute.MutableAttributeMap
import processm.core.log.attribute.mutableAttributeMapOf
import processm.core.logging.logger
import processm.core.persistence.connection.DBCache
import processm.dbmodels.models.AutomaticEtlProcess
import processm.dbmodels.models.Class
import processm.dbmodels.models.Classes
import processm.etl.tracker.DatabaseChangeApplier
import java.sql.Connection
import java.sql.JDBCType
import java.time.Instant
import java.util.*

/**
 * The logic of processing events in automatic ETL
 *
 * @param dataStoreDBName A data store where the log is to be created
 * @param logId An identifier of the ETL process and the resulting log
 * @param descriptor A mapping from the remote DB to the log
 */
class AutomaticEtlProcessExecutor(
    val dataStoreDBName: String, val logId: UUID, val descriptor: DAGBusinessPerspectiveDefinition
) {

    @Deprecated(message = "Use the primary constructor instead")
    constructor(
        dataStoreDBName: String, logId: UUID, graph: Graph<EntityID<Int>, Arc>, identifyingClasses: Set<EntityID<Int>>
    ) : this(dataStoreDBName, logId, DAGBusinessPerspectiveDefinition(graph, identifyingClasses))

    private val metaModelId =
        checkNotNull(Class.findById(descriptor.graph.vertexSet().first())?.dataModel?.id) { "The DB is broken" }

    private val classIds = HashMap<String, EntityID<Int>>()
    private fun getClassId(className: String): EntityID<Int> = classIds.computeIfAbsent(className) {
        Classes.slice(Classes.id).select { (Classes.name eq className) and (Classes.dataModelId eq metaModelId) }
            .firstOrNull()?.getOrNull(Classes.id)
            ?: throw NoSuchElementException("A class with the specified name $className does not exist")
    }

    private fun Connection.retrieveObjectGraphs(leafs: Collection<RemoteObjectID>): Sequence<ObjectGraph> = sequence {
        prepareStatement(
            """
            select distinct objects::text 
            from traces join logs on traces.log_id=logs.id 
            where              
            logs."identity:id"=?::uuid 
            and objects ??| ?::text[]
            """.trimIndent()
        ).use { stmt ->
            stmt.setString(1, logId.toString())
            stmt.setArray(2, createArrayOf(JDBCType.VARCHAR.name, leafs.mapToArray(RemoteObjectID::toDB)))
            stmt.executeQuery().use { rs ->
                while (rs.next()) {
                    val objectGraph = HashMap<RemoteObjectID, Map<String, Map<EntityID<Int>, Set<RemoteObjectID>>>>()
                    for ((sourceDB, attributeToTarget) in Json.parseToJsonElement(rs.getString(1)) as JsonObject) {
                        val source = sourceDB.parseAsRemoteObjectID()
                        objectGraph[source] = (attributeToTarget as JsonObject).mapValues { (_, targets) ->
                            val result = HashMap<EntityID<Int>, HashSet<RemoteObjectID>>()
                            for (targetDB in targets as JsonArray) {
                                val target = (targetDB as JsonPrimitive).content.parseAsRemoteObjectID()
                                result.computeIfAbsent(target.classId) { HashSet() }.add(target)
                            }
                            return@mapValues result
                        }

                    }
                    yield(objectGraph)
                }
            }
        }
    }


    private fun Connection.getRelatedIdentifyingObjects(
        start: RemoteObjectID, newAttributes: Map<String, String>
    ): Set<RemoteObjectID> {
        val leafs = mutableListOf(start)
        descriptor.graph.outgoingEdgesOf(start.classId).mapNotNullTo(leafs) { arc ->
            newAttributes[arc.attributeName]?.let { parentId -> RemoteObjectID(parentId, arc.targetClass) }
        }

        val result = HashSet<RemoteObjectID>()
        for (objectGraph in retrieveObjectGraphs(leafs)) {
            val upwards = objectGraph.upwards(leafs, descriptor)
            result.addAll(upwards)
            result.addAll(objectGraph.reverse().downwards(upwards, descriptor))
        }
        result.addAll(leafs)
        result.remove(start)
        return result
    }

    /**
     * Converts a [DatabaseChangeApplier.DatabaseChangeEvent] to XES:
     * * A random `identity:id` is generated
     * * The DB identifier of the underlying DB object is stored as `org:resource`
     * * The identifier of the table ([classId]) the object comes from is stored using a non-standard attribute [CLASSID_ATTR]
     * * All the object's attributes present in the event are stored in as non-stadnard attributes in the [DB_ATTR_NS] namespace
     */
    private fun DatabaseChangeApplier.DatabaseChangeEvent.toXES(classId: EntityID<Int>) = Event(mutableAttributeMapOf(
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

    /**
     * Retrieve all events describing any of the objects given in [objects]
     */
    private fun Connection.retrievePastEvents(objects: Collection<RemoteObjectID>): List<Event> {
        assert(objects.isNotEmpty())
        // This seems to be more efficient than using PQL
        // TODO Consider moving the actual copying to the DB - see #185
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
        val query = buildSQLQuery {
            append(
                """select distinct traces."identity:id"
             from traces,logs
             where traces.log_id=logs.id and logs."identity:id"=?::uuid and objects ??& ?::text[]"""
            )
            bind(logId)
            bind(objects)
        }
        return prepareStatement(query).use { stmt ->
            return@use stmt.executeQuery().use { rs ->
                val result = HashSet<UUID>()
                while (rs.next()) {
                    rs.getString(1).toUUID()?.let { result.add(it) }
                }
                return@use result
            }
        }
    }

    /**
     * Returns all traces in the given log containing the given objects along with all the contained objects.
     */
    private fun Connection.retrievePastTracesWithObjects(objects: Collection<RemoteObjectID>): Map<UUID, Set<RemoteObjectID>> {
        assert(objects.isNotEmpty())
        val query = buildSQLQuery {
            append(
                """select "identity:id", array_agg(object) as objects from (
                select traces."identity:id", jsonb_object_keys(traces.objects) as object  from traces,logs
                where traces.log_id=logs.id and logs."identity:id"=?::uuid and objects ??& ?::text[]
                ) x group by "identity:id"
                """
            )
            bind(logId)
            bind(objects)
        }
        return prepareStatement(query).use { stmt ->
            return@use stmt.executeQuery().use { rs ->
                val result = HashMap<UUID, HashSet<RemoteObjectID>>()
                while (rs.next()) {
                    val traceId = checkNotNull(rs.getString(1).toUUID())
                    result[traceId] =
                        (rs.getArray(2).array as Array<*>).mapTo(HashSet()) { (it as String).parseAsRemoteObjectID() }
                }
                return@use result
            }
        }
    }

    /**
     * Extract the information about the object from an [Event].
     * Returns `null` if the event does not describe any object.
     */
    private fun Event.getObject(): RemoteObjectID? {
        val resource = orgResource ?: return null
        val classId = (attributes[CLASSID_ATTR] as Long?) ?: return null
        return RemoteObjectID(resource, EntityID(classId.toInt(), Classes))
    }

    /**
     * A persistent queue to store events that cannot be currently appended to the log.
     */
    private val queue = PersistentPostponedEventsQueue(logId)

    /**
     * The main logic of events processing, possibly called multiple times for the same event.
     * If the processing succeeded, it returns a non-empty list of [XESComponent]s, consisting of the XES representation
     * of [dbEvent] along [Trace] entries indicating where the event should be added.
     * Since the log is obvious, a [Log] object is not returned by this function.
     * If a new trace is created, there may also be [Event]s retrieved from the DB that must be present in the new trace as well.
     * If the process fails, an empty list is returned.
     *
     * For the details on how the function works, see the in-code comments.
     */
    private fun Connection.processEvent(dbEvent: DatabaseChangeApplier.DatabaseChangeEvent): List<XESComponent> {
        val result = ArrayList<XESComponent>()
        val remoteObject = dbEvent.getObject()
        val relevantTraces = retrievePastTraces(setOf(remoteObject))
        val event = dbEvent.toXES(remoteObject.classId)
        if (relevantTraces.isNotEmpty()) {
            /** The event is an update of an object already present in the log.
             * It is sufficient to append the event to all the traces referencing that object.
             */
            for (traceId in relevantTraces) {
                result.add(Trace(mutableAttributeMapOf(Attribute.IDENTITY_ID to traceId)))
                result.add(event)
            }
        } else {
            /**
             * The event describes an object that is new to the log.
             * Using the information from the event and from the DB, retrieve all objects from the identifying classes
             * that are related to the object according to the graph of the process.
             */
            val identifyingRelatedObjects = getRelatedIdentifyingObjects(remoteObject, dbEvent.objectData)
            assert(remoteObject !in identifyingRelatedObjects)
            if (remoteObject.classId in descriptor.identifyingClasses) {
                /**
                 * If the object itself belongs to one of the identifying classes, we begin by computing the set of
                 * possible case identifiers and finding traces with these objects.
                 * A case identifier is a set of objects from the identifying classes, at most one object from every class.
                 * A trace is uniquely identified by its case identifier.
                 *
                 * Ordinarily, this path should not fail (i.e., the event should not be postponed).
                 * However, it is possible the DB processes events "out-of-order", e.g., because constraints are
                 * disabled and thus a referencing object can be inserted before the referenced object.
                 */
                assert(identifyingRelatedObjects.none { it.classId == remoteObject.classId })
                val possibleCIs = identifyingRelatedObjectsToPossibleCaseIdentifiers(identifyingRelatedObjects)
                val existingTraceIds = ArrayList<UUID>()
                for (ci in possibleCIs) {
                    if (ci.isNotEmpty()) {
                        for ((traceId, objs) in retrievePastTracesWithObjects(ci)) {
                            if (objs.filterTo(HashSet()) { it.classId in descriptor.identifyingClasses } == ci) existingTraceIds.add(
                                traceId
                            )
                        }
                    }
                }
                /**
                 *  If any trace was found, simply append the event to them.
                 */
                if (existingTraceIds.isNotEmpty()) {
                    for (traceId in existingTraceIds) {
                        result.add(Trace(mutableAttributeMapOf(Attribute.IDENTITY_ID to traceId)))
                        result.add(event)
                    }
                } else {
                    /**
                     * Otherwise, create new traces, retrieving relevant events about objects from the identifying
                     * classes if necessary.
                     */
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
                /**
                 * A new object that belongs to a converging class.
                 * If there are traces that the event can be appended to, we find them.
                 * Otherwise, we return an empty list - the event is postponed.
                 */
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
        DBCache.get(dataStoreDBName).getConnection().use { connection ->
            //No use on AppendingDBXESOutputStream to avoid closing the connection
            val output = AppendingDBXESOutputStream(connection)
            output.write(Log(mutableAttributeMapOf(Attribute.IDENTITY_ID to logId)))
            output.write(asSequence())
            output.flush()
            // Add object described by an event to the objects column of the event's trace
            connection.prepareStatement(
                """
                update traces
                set objects = jsonb_build_object(?::text, '{}'::jsonb) || objects
                where "identity:id"=?::uuid and not (objects ?? ?::text)
            """.trimIndent()
            ).use { stmt ->
                var currentTraceIdentityId: String? = null
                for (component in this) {
                    if (component is Trace) currentTraceIdentityId = component.identityId?.toString()
                    else if (component is Event) {
                        val dbId = component.getObject()?.toDB() ?: continue
                        stmt.setString(1, dbId)
                        stmt.setString(2, checkNotNull(currentTraceIdentityId))
                        stmt.setString(3, dbId)
                        stmt.executeUpdate()
                    }
                }
            }
            // Add attribute values relevant according to the relation graph
            connection.prepareStatement(
                """
                    update traces 
                    set objects=
                    jsonb_set(
                        objects, 
                        ?::text[], 
                        jsonb_build_array(?::text) || coalesce((objects #> ?::text[]), '[]'::jsonb), 
                        true
                    ) 
                    where not (
                        jsonb_build_object(?::text, jsonb_build_object(?::text, jsonb_build_array(?::text))) 
                        <@ objects
                    ) and "identity:id"=?::uuid""".trimIndent()
            ).use { stmt ->
                var currentTraceIdentityId: String? = null
                for (component in this) {
                    if (component is Trace) currentTraceIdentityId = component.identityId?.toString()
                    else if (component is Event) {
                        val remoteObjectId = component.getObject() ?: continue
                        val source = remoteObjectId.toDB()
                        for (r in descriptor.graph.outgoingEdgesOf(remoteObjectId.classId)) {
                            val targetObjectId =
                                component.attributes.getOrNull("$DB_ATTR_NS:${r.attributeName}")?.toString()
                                    ?: continue
                            val target = "${r.targetClass.value}${RemoteObjectID.DELIMITER}${targetObjectId}"
                            val path =
                                connection.createArrayOf(JDBCType.VARCHAR.name, arrayOf(source, r.attributeName))
                            stmt.setArray(1, path)
                            stmt.setString(2, target)
                            stmt.setArray(3, path)
                            stmt.setString(4, source)
                            stmt.setString(5, r.attributeName)
                            stmt.setString(6, target)
                            stmt.setString(7, checkNotNull(currentTraceIdentityId))
                            stmt.executeUpdate()
                        }
                    }

                }
                connection.commit()
            }
        }
    }

    private fun DatabaseChangeApplier.DatabaseChangeEvent.getObject() =
        RemoteObjectID(entityId, getClassId(entityTable))

    /**
     * Process queue of postponed events. The events are processed in the FIFO order using [Connection.processEvent].
     * If any was processed successfully, it is written to the DB and removed from the queue.
     * After the whole queue is processed, if any event was processed successfully, the queue is processed again.
     */
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
                    } else unprocessedObjects.add(obj)
                }
            }
            if (!modified) break
        }
    }

    /**
     * The point where a new event from the DB monitoring arrives.
     * Each event describes a single DB object.
     * If any event in the [queue] describes the same object, the event is added to the end of the [queue], no
     * processing is performed and the function returns `false`.
     * Otherwise, [Connection.processEvent] is called.
     * If it returns with a non-empty collection of [XESComponent]s, they are written to the DB, and the queue is
     * processed with [processQueue]. Finally, `true` is returned.
     * Otherwise, `false` is returned.
     */
    fun processEvent(dbEvent: DatabaseChangeApplier.DatabaseChangeEvent): Boolean {
        DBCache.get(dataStoreDBName).getConnection().use { connection ->
            logger.trace("For the first time {}", dbEvent)
            val objectOfEvent = dbEvent.getObject()
            // Process an event only if there are no unprocessed events related to this object
            val result = if (!queue.hasObject(objectOfEvent)) connection.processEvent(dbEvent)
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

        val DB_ATTR_NS = "db"
        val INTERNAL_NS = "${Brand.mainDBInternalName}:internal"
        val CLASSID_ATTR = "$INTERNAL_NS:classId"

        val logger = logger()

        /**
         * Read the definition of the automatic ETL process identified by [automaticEtlProcessId] from the datastore [dataStoreDBName]
         */
        fun fromDB(
            dataStoreDBName: String, automaticEtlProcessId: UUID
        ): AutomaticEtlProcessExecutor {
            val relations = AutomaticEtlProcess.findById(automaticEtlProcessId)?.relations ?: error("Process not found")
            check(!relations.empty()) { "An automatic ETL process must refer to some relations " }
            val graph = DefaultDirectedGraph<EntityID<Int>, Arc>(Arc::class.java)
            for (relation in relations) {
                val arc = Arc(relation.sourceClass.id, relation.referencingAttributesName.name, relation.targetClass.id)
                graph.addVertex(relation.sourceClass.id)
                graph.addVertex(relation.targetClass.id)
                graph.addEdge(relation.sourceClass.id, relation.targetClass.id, arc)
            }
            return AutomaticEtlProcessExecutor(
                dataStoreDBName, automaticEtlProcessId,
                DAGBusinessPerspectiveDefinition(graph, graph.vertexSet())
            )

        }
    }
}