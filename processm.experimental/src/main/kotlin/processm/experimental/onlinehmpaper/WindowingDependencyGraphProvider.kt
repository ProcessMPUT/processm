package processm.experimental.onlinehmpaper

import processm.core.helpers.mapToSet
import processm.core.models.causalnet.Dependency
import processm.core.models.causalnet.Node
import processm.experimental.heuristicminer.dependencygraphproviders.DependencyGraphProvider
import processm.miners.onlineminer.NodeTrace

class WindowingDependencyGraphProvider(val windowSize: Int) : DependencyGraphProvider {

    override val start = Node("start", special = true)
    override val end = Node("end", special = true)
    private var epoch = 0
    private val directlyFollows = HashMap<Dependency, Int>()


    override fun processTrace(nodeTrace: NodeTrace) {
        val i = nodeTrace.iterator()
        var prev = start
        while (i.hasNext()) {
            val curr = i.next()
            directlyFollows[Dependency(prev, curr)] = epoch
            prev = curr
        }
        directlyFollows[Dependency(prev, end)] = epoch
        epoch++
    }

    override fun computeDependencyGraph(): Set<Dependency> =
        directlyFollows
            .filterValues { last -> last >= epoch - windowSize }
            .keys

    override val nodes: Set<Node>
        get() = computeDependencyGraph().mapToSet { it.source } + setOf(end)
}