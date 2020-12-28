package processm.etl.metamodel

import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.sql.transactions.transaction
import org.jgrapht.Graph
import org.jgrapht.alg.connectivity.ConnectivityInspector
import org.jgrapht.alg.shortestpath.DijkstraShortestPath
import org.jgrapht.alg.spanning.KruskalMinimumSpanningTree
import org.jgrapht.graph.AsSubgraph
import org.jgrapht.graph.DefaultUndirectedGraph
import processm.core.helpers.mapToSet
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
     * Discovers case notions consisting of the provided classes.
     *
     * @param rootClass Case notion's root class.
     * @param allowedClasses Set of classes which are allowed to be present in the resulting case notions.
     * @param identifyingClasses Set of case notion's identifying classes.
     * @return A map containing case notions and their relevance score.
     * @see CaseNotionDefinition.rootClass
     * @see CaseNotionDefinition.identifyingClasses
     */
    fun discoverCaseNotions(rootClass: String, allowedClasses: Set<String>, identifyingClasses: Set<String>): Map<CaseNotionDefinition<EntityID<Int>>, Double>
            = transaction(DBCache.get(targetDatabaseName).database) {
        val rootClassId = metaModelReader.getClassId(rootClass)
        val identifyingClassesIds = identifyingClasses.mapToSet { metaModelReader.getClassId(it) }
        val relationshipGraph: Graph<EntityID<Int>, String> = DefaultUndirectedGraph(String::class.java)

        metaModelReader.getRelationships()
            .forEach { (relationshipName, relationship) ->
                val (referencingClassId, referencedClassId) = relationship

                if (!allowedClasses.contains(metaModelReader.getClassName(referencingClassId))
                    || !allowedClasses.contains(metaModelReader.getClassName(referencedClassId))) return@forEach

                relationshipGraph.addVertex(referencingClassId)
                relationshipGraph.addVertex(referencedClassId)
                relationshipGraph.addEdge(referencingClassId, referencedClassId, relationshipName)
            }

        val rootConnectedSet = ConnectivityInspector(relationshipGraph)
            .connectedSetOf(rootClassId)

        return@transaction discoverCaseNotionsStartingWithRoot(
            rootClassId, rootConnectedSet, identifyingClassesIds, relationshipGraph)
    }

    private fun discoverCaseNotionsStartingWithRoot(
        rootName: EntityID<Int>,
        allowedClasses: Set<EntityID<Int>>,
        identifyingClasses: Set<EntityID<Int>>,
        relationshipGraph: Graph<EntityID<Int>, String>): Map<CaseNotionDefinition<EntityID<Int>>, Double> {
        val relationshipSubgraph = AsSubgraph(relationshipGraph, allowedClasses)
        val shortestPaths = DijkstraShortestPath(relationshipSubgraph).getPaths(rootName)
        val spanningTree = KruskalMinimumSpanningTree(relationshipSubgraph).spanningTree
        val caseNotionTree = mutableMapOf<EntityID<Int>, EntityID<Int>?>(rootName to null)

        spanningTree.edges.map {
            val source = relationshipSubgraph.getEdgeSource(it)
            val target = relationshipSubgraph.getEdgeTarget(it)

            if (shortestPaths.getPath(source).length > shortestPaths.getPath(target).length) caseNotionTree[source] = target
            else caseNotionTree[target] = source
        }

        return mapOf(CaseNotionDefinition(caseNotionTree, identifyingClasses) to spanningTree.edges.size.toDouble())
    }

    // this is quick and dirty implementation
    private fun <T> Set<T>.powerSet(): Set<Set<T>> {
        val sets: MutableSet<Set<T>> = HashSet()
        if (this.isEmpty()) {
            sets.add(HashSet())
            return sets
        }
        val list: List<T> = ArrayList(this)
        val head = list[0]
        val rest: Set<T> = HashSet(list.subList(1, list.size))
        for (set in rest.powerSet()) {
            val newSet: MutableSet<T> = HashSet()
            newSet.add(head)
            newSet.addAll(set)
            sets.add(newSet)
            sets.add(set)
        }
        return sets
    }
}