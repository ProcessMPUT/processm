package processm.etl.metamodel

import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.sql.transactions.transaction
import org.jgrapht.Graph
import org.jgrapht.GraphTests
import org.jgrapht.alg.connectivity.ConnectivityInspector
import org.jgrapht.alg.shortestpath.DijkstraShortestPath
import org.jgrapht.alg.spanning.KruskalMinimumSpanningTree
import org.jgrapht.graph.AsSubgraph
import org.jgrapht.graph.DefaultDirectedGraph
import processm.core.helpers.mapToSet
import processm.core.persistence.connection.DBCache
import java.util.*
import kotlin.collections.ArrayDeque
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * Explores possible business perspectives and evaluates their potential relevance.
 *
 * @param dataStoreDBName Name of database containing meta model data.
 * @param metaModelReader Component for acquiring meta model data.
 */
class DAGBusinessPerspectiveExplorer(
    private val dataStoreDBName: String,
    private val metaModelReader: MetaModelReader
) {

    /**
     * Discovers business process perspectives.
     *
     * @param performFullSearch Indicates whether to perform exhaustive search of the possible business perspectives space.
     * @param goodEnoughScore If a business perspective has . Used for search space pruning.
     * @return A collection containing discovered business perspectives and their respective relevance scores.
     */
    fun discoverBusinessPerspectives(
        performFullSearch: Boolean = false,
        goodEnoughScore: Double = 0.0
    ): List<Pair<DAGBusinessPerspectiveDefinition<EntityID<Int>>, Double>> =
        transaction(DBCache.get(dataStoreDBName).database) {
            val relationshipGraph: Graph<EntityID<Int>, String> = getRelationshipGraph()
            val weights = relationshipGraph.calculateVertexWeights()

            return@transaction setOf(relationshipGraph)
                .flatMap {
                    it.searchForOptimumBottomUp(weights, performFullSearch, goodEnoughScore)
                        .map { DAGBusinessPerspectiveDefinition(it.first) to it.second }
                }
        }

    fun getRelationshipGraph(): Graph<EntityID<Int>, String> = transaction(DBCache.get(dataStoreDBName).database) {
        val relationshipGraph: Graph<EntityID<Int>, String> = DefaultDirectedGraph(String::class.java)

        metaModelReader.getRelationships()
            .forEach { (relationshipName, relationship) ->
                val (referencingClassId, referencedClassId) = relationship

                relationshipGraph.addVertex(referencingClassId)
                relationshipGraph.addVertex(referencedClassId)

                // self-loop, not supported at the moment
                if (referencingClassId != referencedClassId) {
                    relationshipGraph.addEdge(referencingClassId, referencedClassId, relationshipName)
                }
            }

        return@transaction relationshipGraph
    }
}

private fun Graph<EntityID<Int>, String>.splitByEdge(splittingEdge: String): Set<Graph<EntityID<Int>, String>> {
    val splitGraph = AsSubgraph(this, vertexSet(), edgeSet() - splittingEdge)
    val connectedSets = ConnectivityInspector(splitGraph).connectedSets()

    return connectedSets.mapToSet { AsSubgraph(splitGraph, it) }
}

private fun Graph<EntityID<Int>, String>.splitByVertex(splittingVertex: EntityID<Int>): Set<Graph<EntityID<Int>, String>> {
    val splitGraph = AsSubgraph(this, vertexSet() - splittingVertex)
    val connectedSets = ConnectivityInspector(splitGraph).connectedSets()

    return connectedSets.mapToSet { AsSubgraph(splitGraph, it) }
}

private fun Graph<EntityID<Int>, String>.searchForOptimumTopDown(
    vertexWeights: Map<EntityID<Int>, Double>,
    performFullSearch: Boolean,
    goodEnoughScore: Double
): List<Pair<Graph<EntityID<Int>, String>, Double>> {
    val bestSolutions = mutableSetOf<Pair<Graph<EntityID<Int>, String>, Double>>()
    val subgraphQueue = ArrayDeque(setOf((this to 0) to calculateEdgeHeterogeneity()))
    val edgeOrder = getEdgeOrderingByDistance()

    while (subgraphQueue.isNotEmpty()) {
        val (graphInfo, parentScore) = subgraphQueue.removeLast()
        val (parentGraph, parentGraphInducingEdge) = graphInfo
        val childGraphs = if (parentScore <= goodEnoughScore) emptyList() else
            parentGraph.edgeSet()
                .filter { edgeOrder[it]!! >= parentGraphInducingEdge }
                .flatMap { edgeName ->
                    val subgraphs = parentGraph.splitByEdge(edgeName)
                    val edgeOrdering = edgeOrder[edgeName]!!

                    return@flatMap subgraphs.filter { it.vertexSet().size > 1 }
                        .map { subgraph -> (subgraph to edgeOrdering) to subgraph.calculateEdgeHeterogeneity() }
                        .filter { performFullSearch || it.second < parentScore }
                }

        val bestChild = childGraphs.minByOrNull { it.first.second }
        if (bestChild != null) subgraphQueue.add(bestChild)
        if (bestChild == null || performFullSearch) bestSolutions.add(parentGraph to parentScore)
    }

    return bestSolutions.sortedWith(
        compareBy(
            { it.second },
            { -it.first.vertexSet().sumOf { vertexWeights[it]!! } / it.first.vertexSet().size },
            { (it.first.vertexSet().size - 1) * it.first.vertexSet().size / it.first.edgeSet().size }
        ))
}

private fun Graph<EntityID<Int>, String>.searchForOptimumBottomUp(
    vertexWeights: Map<EntityID<Int>, Double>,
    performFullSearch: Boolean,
    goodEnoughScore: Double
): List<Pair<Graph<EntityID<Int>, String>, Double>> {
    val bestSolutions = mutableSetOf<Pair<Graph<EntityID<Int>, String>, Double>>()
    val edgeOrder = getEdgeOrderingByDistance()
    val acceptableSize = 4..10
    val supergraphQueue = ArrayDeque(
        edgeSet().map { edgeName ->
            (AsSubgraph(
                this,
                setOf(getEdgeSource(edgeName), getEdgeTarget(edgeName)),
                setOf(edgeName)
            ) to edgeOrder[edgeName]!!) to goodEnoughScore
        })

    while (supergraphQueue.isNotEmpty()) {
        val (graphInfo, parentScore) = supergraphQueue.removeLast()
        val (parentGraph, parentGraphInducingEdge) = graphInfo
        val derivedGraphs =
            if (parentScore <= goodEnoughScore && parentGraph.vertexSet().size in acceptableSize) emptyList() else edgeSet()
                .asSequence()
                .filter { edgeOrder[it]!! >= parentGraphInducingEdge }
                .filter { !parentGraph.containsEdge(it) }
                .filter { parentGraph.containsVertex(getEdgeSource(it)) || parentGraph.containsVertex(getEdgeTarget(it)) }
                .map { edgeName ->
                    val edgeOrdering = edgeOrder[edgeName]!!
                    val supergraph = AsSubgraph(
                        this,
                        parentGraph.vertexSet().union(setOf(getEdgeSource(edgeName), getEdgeTarget(edgeName))),
                        parentGraph.edgeSet() + edgeName
                    )

                    return@map (supergraph to edgeOrdering) to supergraph.calculateEdgeHeterogeneity()
                }
                .filter { parentGraph.vertexSet().size < 3 || it.second <= parentScore }.toList()

        supergraphQueue.addAll(derivedGraphs)

        if (parentGraph.vertexSet().size + 1 in acceptableSize && derivedGraphs.isEmpty()) bestSolutions.add(parentGraph to parentScore)
    }

    return bestSolutions.sortedWith(
        compareBy(
            { it.second },
            { -it.first.vertexSet().sumOf { vertexWeights[it]!! } / it.first.vertexSet().size },
            { (it.first.vertexSet().size - 1) * it.first.vertexSet().size / it.first.edgeSet().size }
        ))
}

private fun Graph<EntityID<Int>, String>.getEdgesWithCommonVertex(edge: String): Set<String> {
    return edgesOf(getEdgeSource(edge)).union(edgesOf(getEdgeTarget(edge))) - edge
}

private fun Graph<EntityID<Int>, String>.getEdgeOrdering(): Map<String, Int> {
    return edgeSet().zip(1..edgeSet().size).toMap()
}

// this produces good results much faster than getEdgeOrdering()
private fun Graph<EntityID<Int>, String>.getEdgeOrderingByDistance(): Map<String, Int> {
    val edges = edgeSet().toTypedArray()
    val edgeDistance = mutableMapOf<String, Double>()

    for (i in edges.indices) {
        for (j in (i + 1)..(edges.size - 1)) {
            val distance = calculateDistanceBetweenTwoEdgesL1(edges[i], edges[j])
            edgeDistance.merge(edges[i], distance, Double::plus)
            edgeDistance.merge(edges[j], distance, Double::plus)
        }
    }
    return edgeDistance
        .toList()
        .sortedByDescending { entry -> entry.second }
        .mapIndexed { index, entry -> entry.first to index }
        .toMap()
}

private fun Graph<EntityID<Int>, String>.calculateVertexWeights(): Map<EntityID<Int>, Double> {
    val vertexWeights = mutableMapOf<EntityID<Int>, Double>()
    val unassignedVertices = mutableSetOf<EntityID<Int>>()

    vertexSet().forEach {
        if (outDegreeOf(it) == 0) vertexWeights[it] = 1.0 else unassignedVertices.add(it)
    }

    // simple cycles are not supported at the moment
    while (unassignedVertices.isNotEmpty()) {
        unassignedVertices.forEach { vertex ->
            val successors = outgoingEdgesOf(vertex).map { getEdgeTarget(it) }

            if (vertexWeights.keys.containsAll(successors)) {
                vertexWeights[vertex] = successors.maxOf { vertexWeights[it]!! } + 1
            }
        }

        unassignedVertices.removeAll(vertexWeights.keys)
    }

    return vertexWeights
}

private fun Graph<EntityID<Int>, String>.calculateEdgeHeterogeneity(): Double {
    val edges = edgeSet().toTypedArray()
    var graphHeterogeneity = 0.0

    for (i in edges.indices) {
        for (j in (i + 1)..(edges.size - 1)) {
            graphHeterogeneity += calculateDistanceBetweenTwoEdgesL1(edges[i], edges[j])
        }
    }

    return graphHeterogeneity / (edges.size.toDouble().pow(3.0) / 4.0)
}

private fun Graph<EntityID<Int>, String>.calculateDistanceBetweenTwoEdgesL1(edge1: String, edge2: String): Double {
    val outDegreeDiff = outDegreeOf(getEdgeSource(edge1)) - outDegreeOf(getEdgeSource(edge2))
    val inDegreeDiff = inDegreeOf(getEdgeTarget(edge1)) - inDegreeOf(getEdgeTarget(edge2))

    return abs(outDegreeDiff).plus(abs(inDegreeDiff)).toDouble()
}

private fun Graph<EntityID<Int>, String>.calculateDistanceBetweenTwoEdgesL2(edge1: String, edge2: String): Double {
    val outDegreeDiff = outDegreeOf(getEdgeSource(edge1)) - outDegreeOf(getEdgeSource(edge2))
    val inDegreeDiff = inDegreeOf(getEdgeTarget(edge1)) - inDegreeOf(getEdgeTarget(edge2))

    return hypot(outDegreeDiff.toDouble(), inDegreeDiff.toDouble())
}

private fun AsSubgraph<EntityID<Int>, String>.calculateHomogeneity(): Double {
    return edgeSet()
        .map { edgeName -> outDegreeOf(getEdgeSource(edgeName)).toDouble() to inDegreeOf(getEdgeTarget(edgeName)).toDouble() }
        .sumOf { (outDegree, inDegree) ->
            1 / outDegree + 1 / inDegree - 2 / sqrt(outDegree * inDegree)
        }
}

private fun AsSubgraph<EntityID<Int>, String>.calculateNormalizedHomogeneity(): Double {
    val vertexCount = vertexSet().count()
    val homogeneity = calculateHomogeneity()

    return if (homogeneity == 0.0) 0.0 else homogeneity / ((vertexCount * (vertexCount - 2 * sqrt(vertexCount - 1.0))) / (vertexCount - 1))
}