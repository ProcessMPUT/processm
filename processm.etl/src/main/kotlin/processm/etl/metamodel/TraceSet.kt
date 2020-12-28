package processm.etl.metamodel

import org.jetbrains.exposed.dao.id.EntityID
import java.lang.Exception

class TraceSet<TId>(val caseNotionDefinition: CaseNotionDefinition<EntityID<Int>>, val rootObjectsIds: Map<TId, List<EntityID<Int>>>) : Iterable<Map<EntityID<Int>, Map<TId, List<EntityID<Int>>>>> {
    private val objectsTree = mutableMapOf<Pair<EntityID<Int>, TId>?, MutableMap<EntityID<Int>, Map<TId, List<EntityID<Int>>>>>(null to mutableMapOf(caseNotionDefinition.rootClass to rootObjectsIds))

    fun addRelatedObjects(objectId: TId, objectClassId: EntityID<Int>, relatedObjectsIds: Map<TId, List<EntityID<Int>>>, successorsClassId: EntityID<Int>) {
        if (!caseNotionDefinition.hasRelation(successorsClassId, objectClassId))
            throw Exception("The relation between classes does not exist")

        objectsTree
            .getOrPut(objectClassId to objectId, { mutableMapOf() })
            .put(successorsClassId, relatedObjectsIds)
    }

    override fun iterator(): Iterator<Map<EntityID<Int>, Map<TId, List<EntityID<Int>>>>> = TraceSetIterator(objectsTree, caseNotionDefinition.identifyingClasses)

    /**
     * Enables iterating through traces stored in a tree structure.
     */
    private class TraceSetIterator<TId>(
        private val objectsTree: Map<Pair<EntityID<Int>, TId>?, Map<EntityID<Int>, Map<TId, List<EntityID<Int>>>>>,
        identifyingClasses: Set<EntityID<Int>>) : Iterator<Map<EntityID<Int>, Map<TId, List<EntityID<Int>>>>> {
        private val currentTraceElementsIndices = identifyingClasses.map {it to 0}.toMap().toMutableMap()

        override fun next(): Map<EntityID<Int>, Map<TId, List<EntityID<Int>>>> {
            val (rootClassId, rootObjects) = objectsTree[null]?.mapValues { (_, objectVersionsIds) -> objectVersionsIds.keys.toList() }?.entries?.first()!!
            val currentRoot = rootObjects[currentTraceElementsIndices[rootClassId]!!]
            val traceElementsQueue = ArrayDeque<Pair<EntityID<Int>, TId>>()
            val trace = mutableMapOf(rootClassId to mutableMapOf(currentRoot to objectsTree[null]!![rootClassId]!![currentRoot]!!))
            var classIndexToIncrement = rootClassId
            val classIndicesToReset = mutableSetOf<EntityID<Int>>()

            traceElementsQueue.addFirst(rootClassId to currentRoot )

            while (traceElementsQueue.isNotEmpty()) {
                val currentElement = traceElementsQueue.removeFirst()
                val successors = objectsTree[currentElement]
                var isAnySuccessorNotFullyExplored = false

                successors?.forEach { (successorClassId, successorObjects) ->
                    val successorClassIndex = currentTraceElementsIndices[successorClassId]

                    if (successorClassIndex != null) {
                        if (successorObjects.count() - 1 > successorClassIndex) classIndexToIncrement = successorClassId
                        if (successorObjects.count() - 1 == successorClassIndex) classIndicesToReset.add(successorClassId) else isAnySuccessorNotFullyExplored = true

                        val (successorObjectId, successorObjectVersions) = successorObjects.toList()[successorClassIndex]
                        trace.getOrPut(successorClassId, { mutableMapOf() }).put(successorObjectId, successorObjectVersions)
                        traceElementsQueue.addFirst(successorClassId to successorObjectId)
                    }
                    else {
                        trace.getOrPut(successorClassId, { mutableMapOf() }).putAll(successorObjects)
                        successorObjects.forEach { (successorObjectId, _) ->
                            traceElementsQueue.addFirst(successorClassId to successorObjectId)
                        }
                    }
                }

                if (isAnySuccessorNotFullyExplored) classIndicesToReset.clear()

            }

            currentTraceElementsIndices.merge(classIndexToIncrement, 1, Int::plus)
            classIndicesToReset.forEach { currentTraceElementsIndices[it] = 0 }

            return trace
        }

        override fun hasNext(): Boolean {
            val (rootClass, rootObjects) = objectsTree[null]?.entries?.first()!!
            val rootClassIndex = currentTraceElementsIndices[rootClass]!!

            return rootObjects.count() -1 >= rootClassIndex
        }
    }
}