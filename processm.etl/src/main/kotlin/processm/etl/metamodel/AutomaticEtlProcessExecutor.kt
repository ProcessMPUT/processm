package processm.etl.metamodel

import com.google.common.collect.Lists
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.select
import org.jgrapht.Graph
import org.jgrapht.graph.DefaultDirectedGraph
import org.postgresql.util.PGobject
import processm.core.Brand
import processm.core.helpers.mapToArray
import processm.core.helpers.toUUID
import processm.core.log.*
import processm.core.log.Event
import processm.core.log.attribute.Attribute
import processm.core.log.attribute.mutableAttributeMapOf
import processm.core.logging.logger
import processm.core.persistence.connection.DBCache
import processm.core.querylanguage.Query
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
                    """ child, events_attributes r, objects_in_events parent, events, traces, logs
                        where child.event_id = r.event_id and r.string_value = parent.object_id and
                        r.event_id = events.id and events.trace_id = traces.id and traces.log_id = logs.id and
                        logs."identity:id"=?::uuid and ("""
                )
                bind(logId)
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
        roots: Collection<RemoteObjectID>,
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
            var nextTier = HashSet<Arc>()
            for ((classId, sameClassRoots) in roots.groupBy { it.classId }) {
                val relations = graph.incomingEdgesOf(classId).filter { it.sourceClass !in reject }
                if (relations.isNotEmpty()) {
                    nextTier.addAll(relations)
                    append("(parent.class_id=? and parent.object_id=ANY(?) and (")
                    bind(classId.value)
                    bind(sameClassRoots.mapToArray { it.objectId })
                    for (r in relations) {
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
                    bind("${DB_ATTR_NS}:${r.attributeName}")
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

    private fun retrievePastEvents(objects: Collection<RemoteObjectID>): Sequence<Event> {
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
                append("' and [e:${CLASSID_ATTR}]='") //TODO I suspect a bug in the PQL implementation since '' here should not be needed.
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
    fun processEvent(dbEvent: DatabaseChangeApplier.DatabaseChangeEvent): List<XESComponent> {
        val result = ArrayList<XESComponent>()
        DBCache.get(dataStoreDBName).getConnection().use { connection ->
            result.add(Log(mutableAttributeMapOf(Attribute.IDENTITY_ID to logId)))
            val remoteObject = RemoteObjectID(dbEvent.entityId, getClassId(dbEvent.entityTable))
            val relevantTraces = connection.retrievePastTraces(setOf(remoteObject))
            val relatedObjects = connection.getRelatedObjects(remoteObject, dbEvent.objectData)
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
                            for ((traceId, objs) in connection.retrievePastTracesWithObjects(ci)) {
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
                                result.addAll(retrievePastEvents(ci))
                            result.add(event)
                        }
                    }
                } else {
                    val existingTraceIds =
                        identifyingRelatedObjectsToPossibleCaseIdentifiers(identifyingRelatedObjects).flatMap { ci ->
                            connection.retrievePastTraces(ci)
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
        }
        return result
    }


    companion object {
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
                val attributeName = Relationships.slice(Relationships.referencingAttributeNameId).select {
                    (Relationships.sourceClassId eq relation.sourceClassId) and (Relationships.targetClassId eq relation.targetClassId)
                }.single().let { it[Relationships.name] }
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