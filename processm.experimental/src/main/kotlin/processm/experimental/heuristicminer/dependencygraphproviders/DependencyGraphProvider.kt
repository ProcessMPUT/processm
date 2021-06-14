package processm.experimental.heuristicminer.dependencygraphproviders

import processm.core.models.causalnet.Dependency
import processm.core.models.causalnet.Node
import processm.miners.onlineminer.NodeTrace

/**
 * An abstraction to build a dependency graph as a set of dependencies
 */
interface DependencyGraphProvider {

    /**
     * The start node of the dependency graph
     */
    val start: Node

    /**
     * The end node of the dependency graph
     */
    val end: Node

    /**
     * The set of all nodes of the dependency graph.
     *
     * It may change between subsequent calls to [processTrace]
     */
    val nodes: Set<Node>

    /**
     * Incorporate the knowledge following from [nodeTrace]
     */
    fun processTrace(nodeTrace: NodeTrace)

    /**
     * Construct the dependency graph.
     *
     * The result may change between subsequent calls to [processTrace]
     */
    fun computeDependencyGraph(): Collection<Dependency>

    fun unprocessTrace(nodeTrace: NodeTrace): Unit =
        throw UnsupportedOperationException("By default this operation is unsupported")
}