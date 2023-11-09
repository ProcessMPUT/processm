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
import processm.core.log.attribute.AttributeMap
import processm.core.log.attribute.MutableAttributeMap
import processm.core.log.attribute.mutableAttributeMapOf
import processm.core.logging.logger
import processm.core.persistence.connection.DBCache
import processm.dbmodels.models.*
import processm.etl.tracker.DatabaseChangeApplier
import java.sql.Connection
import java.sql.JDBCType
import java.sql.ResultSet
import java.time.Instant
import java.util.*
import kotlin.collections.ArrayDeque

private inline fun RemoteObjectID.toDB() = "${classId}_$objectId"

private inline fun remoteObjectFromDB(text: String): RemoteObjectID {
    val a = text.split('_', limit = 2)
    return RemoteObjectID(a[1], EntityID(a[0].toInt(), Classes))
}

typealias ObjectGraph = Map<RemoteObjectID, Map<String, Map<EntityID<Int>, Set<RemoteObjectID>>>>

class AutomaticEtlProcessExecutor(
    val dataStoreDBName: String,
    val logId: UUID,
    val graph: Graph<EntityID<Int>, Arc>,
    val identifyingClasses: Set<EntityID<Int>>
) {

    private val convergingClasses = graph.vertexSet() - identifyingClasses

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

    private fun Connection.retrieveObjectGraphs(leafs: Collection<RemoteObjectID>): Sequence<ObjectGraph> =
        sequence {
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
                        val objectGraph =
                            HashMap<RemoteObjectID, Map<String, Map<EntityID<Int>, Set<RemoteObjectID>>>>()
                        for ((sourceDB, attributeToTarget) in Json.parseToJsonElement(rs.getString(1)) as JsonObject) {
                            val source = remoteObjectFromDB(sourceDB)
                            objectGraph[source] =
                                (attributeToTarget as JsonObject).mapValues { (_, targets) ->
                                    val result = HashMap<EntityID<Int>, HashSet<RemoteObjectID>>()
                                    for (targetDB in targets as JsonArray) {
                                        val target = remoteObjectFromDB((targetDB as JsonPrimitive).content)
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

    private fun ObjectGraph.reverse(): ObjectGraph {
        val result = HashMap<RemoteObjectID, HashMap<String, HashMap<EntityID<Int>, HashSet<RemoteObjectID>>>>()
        for ((source, attributes) in this) {
            for ((attribute, targets) in attributes)
                for (target in targets.values.flatten())
                    result
                        .computeIfAbsent(target) { HashMap() }
                        .computeIfAbsent(attribute) { HashMap() }
                        .computeIfAbsent(source.classId) { HashSet() }
                        .add(source)
        }
        return result
    }

    private fun ObjectGraph.upwards(start: Collection<RemoteObjectID>): Set<RemoteObjectID> {
        val localUpward = HashSet<RemoteObjectID>()
        val queue = ArrayDeque<RemoteObjectID>()
        queue.addAll(start)
        while (queue.isNotEmpty()) {
            val item = queue.removeFirst()
            if (item.classId in identifyingClasses)
                localUpward.add(item)
            for (r in graph.outgoingEdgesOf(item.classId)) {
                val candidates = this[item]?.get(r.attributeName)?.get(r.targetClass) ?: continue
                queue.addAll(candidates)
            }
        }
        return localUpward
    }

    private fun ObjectGraph.downwards(
        localUpward: Collection<RemoteObjectID>
    ): Sequence<RemoteObjectID> = sequence {
        val reject = localUpward.mapTo(HashSet()) { it.classId }
        reject.addAll(convergingClasses)
        val queue = ArrayDeque<RemoteObjectID>()
        queue.addAll(localUpward)
        while (queue.isNotEmpty()) {
            val item = queue.removeFirst()
            for (r in graph.incomingEdgesOf(item.classId)) {
                if (r.sourceClass in reject)
                    continue
                val sources = this@downwards[item]?.get(r.attributeName)?.get(r.sourceClass) ?: continue
                queue.addAll(sources)
                yieldAll(sources)
            }
        }
    }

    private fun Connection.getRelatedIdentifyingObjects(
        start: RemoteObjectID,
        newAttributes: Map<String, String>
    ): Set<RemoteObjectID> {
        val leafs = mutableListOf(start)
        graph
            .outgoingEdgesOf(start.classId)
            .mapNotNullTo(leafs) { arc ->
                newAttributes[arc.attributeName]?.let { parentId -> RemoteObjectID(parentId, arc.targetClass) }
            }

        val result = HashSet<RemoteObjectID>()
        for (objectGraph in retrieveObjectGraphs(leafs)) {
            val upwards = objectGraph.upwards(leafs)
            result.addAll(upwards)
            result.addAll(objectGraph.reverse().downwards(upwards))
        }
        result.addAll(leafs)
        result.remove(start)
        return result
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
            append(" from traces,logs")
            append(""" where traces.log_id=logs.id and logs."identity:id"=?::uuid and objects ??& ARRAY[""")
            bind(logId)
            for (o in objects) {
                append("?::text,")
                bind("${o.classId.value}_${o.objectId}")
            }
            deleteCharAt(length - 1)
            append("]")
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
        return queryObjectsInTrace(objects, """traces."identity:id", traces.objects::text""") { rs ->
            val result = HashMap<UUID, Set<RemoteObjectID>>()
            while (rs.next()) {
                val traceId = checkNotNull(rs.getString(1).toUUID())
                val objectsOfTrace = HashSet<RemoteObjectID>()
                result[traceId] = objectsOfTrace
                // TODO move keys extraction to the DB
                (Json.parseToJsonElement(rs.getString(2)) as JsonObject).keys.forEach { o ->
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
        val identifyingRelatedObjects = getRelatedIdentifyingObjects(remoteObject, dbEvent.objectData)
        assert(remoteObject !in identifyingRelatedObjects)
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
                assert(identifyingRelatedObjects.none { it.classId == remoteObject.classId })
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
                    if (component is Trace)
                        currentTraceIdentityId = component.identityId?.toString()
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
            )
                .use { stmt ->
                    var currentTraceIdentityId: String? = null
                    for (component in this) {
                        if (component is Trace)
                            currentTraceIdentityId = component.identityId?.toString()
                        else if (component is Event) {
                            val remoteObjectId = component.getObject() ?: continue
                            val source = remoteObjectId.toDB()
                            for (r in graph.outgoingEdgesOf(remoteObjectId.classId)) {
                                val targetObjectId =
                                    component.attributes.getOrNull("$DB_ATTR_NS:${r.attributeName}")?.toString()
                                        ?: continue
                                val target = "${r.targetClass.value}_${targetObjectId}"
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