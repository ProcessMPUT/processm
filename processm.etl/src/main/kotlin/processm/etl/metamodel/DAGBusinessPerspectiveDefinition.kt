package processm.etl.metamodel

import org.jetbrains.exposed.dao.id.EntityID
import org.jgrapht.Graph
import processm.dbmodels.models.Relationship

/**
 * Represents business perspective as a DAG consisting of business entities' classes.
 * Case notion specifies set of classes used to identify business process instances.
 *
 * @param graph Graph (DAG) consisting of classes and relations included in the business perspective.
 * @param identifyingClasses A subset of nodes of the graph indicating classes (tables) that represent case identifiers,
 *  * i.e., a new object of these classes will create a new trace in the log. Identifying classes as per [https://doi.org/10.1007/s10115-019-01430-6]
 */
data class DAGBusinessPerspectiveDefinition(
    val graph: Graph<EntityID<Int>, Relationship>,
    val identifyingClasses: Set<EntityID<Int>> = graph.vertexSet()
) {
    /**
     * The nodes of the graph that are not [identifyingClasses]
     */
    val convergingClasses = graph.vertexSet() - identifyingClasses

    init {
        val nLeafs = graph.vertexSet().count { graph.outDegreeOf(it) == 0 }
        require(nLeafs == 1) { "The graph must have a single leaf (i.e., a node with an out-degree equal to 0)" }
    }

    /**
     * Returns business perspective classes that the specified class depends on.
     */
    fun getSuccessors(classId: EntityID<Int>) = graph
        .edgesOf(classId)
        .filter { graph.getEdgeSource(it) == classId }
        .map { graph.getEdgeTarget(it) }
}