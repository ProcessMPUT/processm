package processm.experimental.onlinehmpaper

import processm.core.models.causalnet.Dependency
import processm.core.models.causalnet.Node
import processm.helpers.mapToSet
import processm.miners.causalnet.heuristicminer.dependencygraphproviders.DependencyGraphProvider
import processm.miners.causalnet.onlineminer.NodeTrace

class WindowingDependencyGraphProvider(val windowSize: Int) : DependencyGraphProvider {

    override val start = Node("start", isSilent = true)
    override val end = Node("end", isSilent = true)
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

    override fun computeDependencyGraph(): MutableMap<Dependency, Double> {
        val result = HashMap<Dependency, Double>()
        for ((k, last) in directlyFollows)
            if (last >= epoch - windowSize)
                result[k] = Double.NaN
        return result
    }

    override val nodes: Set<Node>
        get() = computeDependencyGraph().keys.mapToSet { it.source } + setOf(end)
}
