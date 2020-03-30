package processm.miners.heuristicminer.dependencygraphproviders

import processm.core.helpers.Counter
import processm.core.models.causalnet.Dependency
import processm.core.models.causalnet.Node
import processm.miners.heuristicminer.NodeTrace

open class DefaultDependencyGraphProvider(
    protected val minDirectlyFollows: Int,
    protected val minDependency: Double
) : DependencyGraphProvider {

    override val start = Node("start", special = true)
    override val end = Node("end", special = true)
    protected val mutableNodes = mutableSetOf(start, end)
    override val nodes: Set<Node> = mutableNodes

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

    internal fun dependency(a: Node, b: Node): Double {
        return if (a != b) {
            val ab = directlyFollows.getOrDefault(Dependency(a, b), 0)
            val ba = directlyFollows.getOrDefault(Dependency(b, a), 0)
            (ab - ba) / (ab + ba + 1.0)
        } else {
            val aa = directlyFollows.getOrDefault(Dependency(a, a), 0)
            aa / (aa + 1.0)
        }
    }


    override fun computeDependencyGraph() =
        directlyFollows
            .filterValues { it >= minDirectlyFollows }
            .keys
            .filter { (a, b) -> dependency(a, b) >= minDependency }


}