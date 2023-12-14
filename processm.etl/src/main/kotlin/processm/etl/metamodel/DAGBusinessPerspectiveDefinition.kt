package processm.etl.metamodel

import org.jetbrains.exposed.dao.id.EntityID
import org.jgrapht.Graph

/**
 * Represents business perspective as a DAG consisting of business entities' classes.
 * Case notion specifies set of classes used to identify business process instances.
 *
 * @param classesGraph Graph (DAG) consisting of classes and relations included in the business perspective.
 * @param caseNotionClassesSelector Selector method used to specify classes which are part of case notion.
 */
class DAGBusinessPerspectiveDefinition(
    private val classesGraph: Graph<EntityID<Int>, String>,
    caseNotionClassesSelector: ((Graph<EntityID<Int>, String>) -> Set<EntityID<Int>>)? = null
) {
    /**
     * Identifying classes as per [https://doi.org/10.1007/s10115-019-01430-6]
     */
    val caseNotionClasses: Set<EntityID<Int>> =
        caseNotionClassesSelector?.invoke(this.classesGraph) ?: this.classesGraph.vertexSet()

    /**
     * Returns business perspective classes that the specified class depends on.
     */
    fun getSuccessors(classId: EntityID<Int>) = classesGraph
        .edgesOf(classId)
        .filter { classesGraph.getEdgeSource(it) == classId }
        .map { classesGraph.getEdgeTarget(it) }
}