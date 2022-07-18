package processm.miners.causalnet.heuristicminer.dependencygraphproviders

import processm.core.helpers.Counter
import processm.core.models.causalnet.Dependency
import processm.core.models.causalnet.Node
import processm.miners.causalnet.onlineminer.NodeTrace

/**
 * Construct a dependency graph by counting pairs of activities directly following each other in presented traces and then thresholding on this count using [minDirectlyFollows]
 *
 * This is equivalent to the basic heuristic miner approach with the threshold for dependency set to -infinity, but it is more efficient to skip computing the dependency measure altogether.
 */
open class BasicDependencyGraphProvider(protected val minDirectlyFollows: Int) : DependencyGraphProvider {

    override val start = Node("start", isSilent = true)
    override val end = Node("end", isSilent = true)
    protected val mutableNodes = mutableSetOf(start, end)
    override val nodes: Set<Node>
        get() = mutableNodes

    internal val directlyFollows = Counter<Dependency>()

    override fun processTrace(nodeTrace: NodeTrace) {
        mutableNodes.addAll(nodeTrace)
        val i = nodeTrace.iterator()
        var prev = start
        while (i.hasNext()) {
            val curr = i.next()
            directlyFollows.inc(Dependency(prev, curr))
            prev = curr
        }
        directlyFollows.inc(Dependency(prev, end))
    }

    override fun unprocessTrace(nodeTrace: NodeTrace) {
        val i = nodeTrace.iterator()
        var prev = start
        while (i.hasNext()) {
            val curr = i.next()
            directlyFollows.dec(Dependency(prev, curr))
            prev = curr
        }
        directlyFollows.dec(Dependency(prev, end))
        mutableNodes.clear()
        val dg = computeDependencyGraph()
        mutableNodes.addAll(dg.map { it.source })
        mutableNodes.addAll(dg.map { it.target })
    }

    override fun computeDependencyGraph(): Collection<Dependency> = directlyFollows
        .filterValues { it >= minDirectlyFollows }
        .keys


}
