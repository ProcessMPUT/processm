package processm.etl.metamodel

import org.jetbrains.exposed.dao.id.EntityID

/**
 * Represents an enumerable collection of traces that conform to the provided business perspective definition.
 */
class TraceSet<TId>(private val businessPerspectiveDefinition: DAGBusinessPerspectiveDefinition<EntityID<Int>>): Iterable<Map<EntityID<Int>, Map<TId, List<EntityID<Int>>>>> {

    private val objectVersions = mutableMapOf<EntityID<Int>, MutableMap<TId, List<EntityID<Int>>>>()
    private val objectRelations = mutableMapOf<Pair<EntityID<Int>, EntityID<Int>>, MutableList<Pair<TId, TId>>>()

    fun addObjectVersions(objectId: TId, objectClassId: EntityID<Int>, objectVersions: List<EntityID<Int>>) {
        this.objectVersions
            .getOrPut(objectClassId) { mutableMapOf() }
            .put(objectId, objectVersions)
    }

    fun addObjectRelations(sourceObjectId: TId, sourceObjectClassId: EntityID<Int>, targetObjectId: TId, targetObjectClassId: EntityID<Int>) {
        objectRelations
            .getOrPut(sourceObjectClassId to targetObjectClassId) { mutableListOf() }
            .add(sourceObjectId to targetObjectId)
    }

    override fun iterator(): Iterator<Map<EntityID<Int>, Map<TId, List<EntityID<Int>>>>> = LogIterator(objectVersions, objectRelations, businessPerspectiveDefinition)

    /**
     * Enables iterating through traces in a DAG structure.
     */
    private class LogIterator<TId>(
        private val objectVersions: Map<EntityID<Int>, MutableMap<TId, List<EntityID<Int>>>>,
        private val objectRelations: Map<Pair<EntityID<Int>, EntityID<Int>>, MutableList<Pair<TId, TId>>>,
        private val businessPerspectiveDefinition: DAGBusinessPerspectiveDefinition<EntityID<Int>>) : Iterator<Map<EntityID<Int>, Map<TId, List<EntityID<Int>>>>> {
        private val identifyingClasses = businessPerspectiveDefinition.caseNotionClasses
        private val classIndices = ClassIndicesVector(identifyingClasses)

        fun isReferencedByAllPredecessors(objectId: TId, objectClassId: EntityID<Int>, predecessingObjects: Map<EntityID<Int>, TId>) =
            businessPerspectiveDefinition.getPredecessors(objectClassId)
                .filter { businessPerspectiveDefinition.isClassIncludedInCaseNotion(it) }
                .all { predecessorClassId ->
                val predecessingObjectId = predecessingObjects[predecessorClassId] ?: return@all false

                objectRelations[predecessorClassId to objectClassId]?.contains(predecessingObjectId to objectId) ?: false
            }

        override fun next(): Map<EntityID<Int>, Map<TId, List<EntityID<Int>>>> {
            val trace: MutableMap<EntityID<Int>, Map<TId, List<EntityID<Int>>>> = mutableMapOf()
            val identifyingObjects = mutableMapOf<EntityID<Int>, TId>()
            var caseNotionClassIndex = 0

            while (caseNotionClassIndex in identifyingClasses.indices) {
                val classId = identifyingClasses[caseNotionClassIndex]
                var objectId: TId? = null
                var objectVersions: List<EntityID<Int>>? = null

                while (objectId == null) {
                    val objectIdIndex = classIndices.getIndexValue(classId)
                    val currentObject = this.objectVersions[classId]?.toList()?.getOrNull(objectIdIndex) ?: break

                    if (isReferencedByAllPredecessors(currentObject.first, classId, identifyingObjects)) {
                        objectId = currentObject.first
                        objectVersions = currentObject.second
                    }
                    else {
                        classIndices.incrementIndexValue(classId)
                    }
                }

                if (objectId == null) {
                    caseNotionClassIndex--
                    classIndices.incrementIndexValue(identifyingClasses[caseNotionClassIndex])
                }
                else {
                    caseNotionClassIndex++
                    identifyingObjects[classId] = objectId
                    trace[classId] = mapOf(objectId to objectVersions.orEmpty())
                }
            }

            classIndices.incrementLastIndexValue()

            return trace
        }

        override fun hasNext(): Boolean {
            // the following logic has been copied from method 'next()'
            val identifyingObjects = mutableMapOf<EntityID<Int>, TId>()
            var caseNotionClassIndex = 0

            while (caseNotionClassIndex in identifyingClasses.indices) {
                val classId = identifyingClasses[caseNotionClassIndex]
                var objectId: TId? = null

                while (objectId == null) {
                    val objectIdIndex = classIndices.getIndexValue(classId)
                    val currentObject = this.objectVersions[classId]?.toList()?.getOrNull(objectIdIndex) ?: break

                    if (isReferencedByAllPredecessors(currentObject.first, classId, identifyingObjects)) {
                        objectId = currentObject.first
                    }
                    else {
                        classIndices.incrementIndexValue(classId)
                    }
                }

                if (objectId == null) {
                    caseNotionClassIndex--
                    if (caseNotionClassIndex < 0) break
                    classIndices.incrementIndexValue(identifyingClasses[caseNotionClassIndex])
                }
                else {
                    caseNotionClassIndex++
                    identifyingObjects[classId] = objectId
                }
            }

            return caseNotionClassIndex > 0
        }

        private class ClassIndicesVector(val classesIds: List<EntityID<Int>>) {
            private val indexValues = classesIds.map { it to 0 }.toMap().toMutableMap()

            fun getIndexValue(classId: EntityID<Int>) = indexValues[classId]!!

            fun incrementIndexValue(classId: EntityID<Int>) {
                val incrementedIndex = classesIds.indexOf(classId)

                indexValues.merge(classId, 1, Int::plus)
                classesIds
                    .filterIndexed { index, _ -> index > incrementedIndex }
                    .forEach { classId ->
                        indexValues[classId] = 0
                    }
            }

            fun incrementLastIndexValue() {
                indexValues.merge(classesIds.last(), 1, Int::plus)
            }
        }
    }
}