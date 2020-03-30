package processm.miners.heuristicminer.dependencygraphproviders

import processm.core.models.causalnet.Dependency
import processm.core.models.causalnet.Node
import processm.miners.heuristicminer.NodeTrace

interface DependencyGraphProvider {
    val start: Node
    val end: Node
    val nodes: Set<Node>
    fun processTrace(nodeTrace: NodeTrace)
    fun computeDependencyGraph(): Collection<Dependency>
}