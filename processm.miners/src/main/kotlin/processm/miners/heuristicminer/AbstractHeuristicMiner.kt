package processm.miners.heuristicminer

import processm.core.log.Event
import processm.core.log.hierarchical.Trace
import processm.core.models.causalnet.Node
import processm.miners.heuristicminer.bindingproviders.BindingProvider

internal typealias NodeTrace = List<Node>

abstract class AbstractHeuristicMiner(
    val minDirectlyFollows: Int,
    val minDependency: Double,
    val bindingProvider: BindingProvider
) : HeuristicMiner {

    internal val start = Node("start", special = true)
    internal val end = Node("end", special = true)

    internal val directlyFollows = Counter<Pair<Node, Node>>()

    internal fun node(e: Event): Node {
        //TODO: do it right once appropriate interface is in place
        return Node(e.conceptName.toString(), e.conceptInstance ?: "")
    }

    protected fun traceToNodeTrace(trace: Trace): NodeTrace =
        trace.events.map { node(it) }.toList()

    protected fun updateDirectlyFollows(nodeTrace: NodeTrace) {
        val i = nodeTrace.iterator()
        var prev = start
        while (i.hasNext()) {
            val curr = i.next()
            directlyFollows.inc(prev to curr)
            prev = curr
        }
        directlyFollows.inc(prev to end)
    }

    internal fun dependency(a: Node, b: Node): Double {
        return if (a != b) {
            val ab = directlyFollows.getOrDefault(a to b, 0)
            val ba = directlyFollows.getOrDefault(b to a, 0)
            (ab - ba) / (ab + ba + 1.0)
        } else {
            val aa = directlyFollows.getOrDefault(a to a, 0)
            aa / (aa + 1.0)
        }
    }

    internal fun computeDependencyGraph() =
        directlyFollows
            .filterValues { it >= minDirectlyFollows }
            .keys
            .filter { (a, b) -> dependency(a, b) >= minDependency }


}