package processm.etl.metamodel

import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.sql.transactions.transaction
import org.jgrapht.Graph
import org.jgrapht.GraphTests
import org.jgrapht.alg.connectivity.ConnectivityInspector
import org.jgrapht.alg.shortestpath.DijkstraShortestPath
import org.jgrapht.alg.spanning.KruskalMinimumSpanningTree
import org.jgrapht.graph.AsSubgraph
import org.jgrapht.graph.DefaultUndirectedGraph
import processm.core.persistence.connection.DBCache
import java.util.*

/**
 * Explores possible case notions and evaluates their potential relevance.
 *
 * @param targetDatabaseName Name of database containing meta model data.
 * @param metaModelReader Component for acquiring meta model data.
 */
class CaseNotionExplorer(private val targetDatabaseName: String, private val metaModelReader: MetaModelReader) {

    /**
     * Discovers case notions consisting of the specified classes.
     *
     * @param rootClassId Case notion's root class.
     * @param allowedClassesIds Set of classes which are allowed to be present in the resulting case notions.
     * @return A list containing case notions.
     * @see CaseNotionDefinition.rootClass
     */
    fun discoverCaseNotions(rootClassId: EntityID<Int>, allowedClassesIds: Set<EntityID<Int>>): List<CaseNotionDefinition<EntityID<Int>>>
            = transaction(DBCache.get(targetDatabaseName).database) {
        val relationshipGraph: Graph<EntityID<Int>, String> = DefaultUndirectedGraph(String::class.java)

        metaModelReader.getRelationships()
            .forEach { (relationshipName, relationship) ->
                val (referencingClassId, referencedClassId) = relationship

                if (!allowedClassesIds.contains(referencingClassId)
                    || !allowedClassesIds.contains(referencedClassId)) return@forEach

                relationshipGraph.addVertex(referencingClassId)
                relationshipGraph.addVertex(referencedClassId)
                relationshipGraph.addEdge(referencingClassId, referencedClassId, relationshipName)
            }

        val rootConnectedSet = ConnectivityInspector(relationshipGraph)
            .connectedSetOf(rootClassId)

        return@transaction discoverCaseNotionsStartingWithRoot(
            rootClassId, rootConnectedSet, relationshipGraph)
    }

    private fun discoverCaseNotionsStartingWithRoot(
        rootClass: EntityID<Int>,
        allowedClasses: Set<EntityID<Int>>,
        relationshipGraph: Graph<EntityID<Int>, String>): List<CaseNotionDefinition<EntityID<Int>>> {
        return allowedClasses.powerSet().fold(mutableListOf()) { caseNotionsDefinitions, allowedClassesSubset ->
            val allowedClassesSubgraph = AsSubgraph(relationshipGraph, allowedClassesSubset)

            if (allowedClassesSubset.contains(rootClass) && GraphTests.isConnected(allowedClassesSubgraph)) {
                val shortestPaths = DijkstraShortestPath(allowedClassesSubgraph).getPaths(rootClass)
                val spanningTree = KruskalMinimumSpanningTree(allowedClassesSubgraph).spanningTree
                val caseNotionTree = mutableMapOf<EntityID<Int>, EntityID<Int>?>(rootClass to null)

                spanningTree.edges.forEach {
                    val source = allowedClassesSubgraph.getEdgeSource(it)
                    val target = allowedClassesSubgraph.getEdgeTarget(it)

                    if (shortestPaths.getPath(source).length > shortestPaths.getPath(target).length)
                        caseNotionTree[source] = target
                    else caseNotionTree[target] = source
                }

                allowedClassesSubset.powerSet().forEach { identifyingClassesSubset ->
                    val identifyingClassesSubgraph = AsSubgraph(allowedClassesSubgraph, identifyingClassesSubset)

                    if (identifyingClassesSubset.contains(rootClass) && GraphTests.isConnected(identifyingClassesSubgraph)) {
                        caseNotionsDefinitions.add(CaseNotionDefinition(caseNotionTree, identifyingClassesSubset))
                    }
                }
            }

            caseNotionsDefinitions
        }
    }

    // this is quick and dirty implementation
    private fun <T> Set<T>.powerSet(): Set<Set<T>> {
        val sets: MutableSet<Set<T>> = HashSet()
        if (this.isEmpty()) {
            sets.add(HashSet())
            return sets
        }
        val list: List<T> = ArrayList(this)
        val head = list.first()
        val rest: Set<T> = HashSet(list.subList(1, list.size))
        for (set in rest.powerSet()) {
            val newSet = mutableSetOf(head)
            newSet.addAll(set)
            sets.add(newSet)
            sets.add(set)
        }
        return sets
    }
}