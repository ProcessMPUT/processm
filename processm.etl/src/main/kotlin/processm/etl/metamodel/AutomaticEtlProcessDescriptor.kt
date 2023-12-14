package processm.etl.metamodel

import org.jetbrains.exposed.dao.id.EntityID
import org.jgrapht.Graph
import processm.dbmodels.models.Class

/**
 * The structure of an automatic ETL process
 *
 * @param graph A graph where nodes correspond to tables in the remote database, identified as [Class] in ProcessM, and
 * arcs indicate attributes where one table references another. The graph must have a single leaf (a node with the out-degree=0).
 * @param identifyingClasses A subset of nodes of the graph indicating classes (tables) that represent case identifiers,
 * i.e., a new object of these classes will create a new trace in the log.
 */
data class AutomaticEtlProcessDescriptor(
    val graph: Graph<EntityID<Int>, Arc>,
    val identifyingClasses: Set<EntityID<Int>>
) {
    //TODO perhaps this class could be unified with [DAGBusinessPerspectiveDefinition]

    /**
     * The nodes of the graph that are not [identifyingClasses]
     */
    val convergingClasses = graph.vertexSet() - identifyingClasses

    init {
        val nLeafs = graph.vertexSet().count { graph.outDegreeOf(it) == 0 }
        require(nLeafs == 1) { "The graph must have a single leaf (i.e., a node with an out-degree equal to 0)" }
    }
}