package processm.miners.heuristicminer.dependencygraphproviders

import processm.core.models.causalnet.Dependency
import processm.core.models.causalnet.Node
import processm.miners.heuristicminer.HashMapWithDefault
import processm.miners.heuristicminer.NodeTrace
import kotlin.math.max
import kotlin.properties.Delegates

class AdaptiveDependencyGraphProvider :
    DependencyGraphProvider {
    override val start = Node("start", special = true)
    override val end = Node("end", special = true)
    private val mutableNodes = mutableSetOf(start, end)
    override val nodes: Set<Node> = mutableNodes

    private val directlyFollows =
        HashMapWithDefault<Dependency, Double>() { 0.0 }
    private var directlyFollowsTh by Delegates.notNull<Double>()

    private fun <T> List<T>.enumerate(): Sequence<Pair<Int, T>> =
        sequence { this@enumerate.forEachIndexed { idx, t -> yield(idx to t) } }

    override fun processTrace(nodeTrace: NodeTrace) {
        mutableNodes.addAll(nodeTrace)
        val tmp = listOf(start) + nodeTrace + listOf(end)
        for ((i, src) in tmp.enumerate()) {
            val right = tmp.subList(i + 1, tmp.size)
            var next = right.indexOf(src)
            val important = if (next >= 0) right.subList(0, next + 1) else right
            for ((j, dst) in important.enumerate())
                directlyFollows[Dependency(src, dst)] += 1.0 / (j + 1)

        }
        directlyFollowsTh = directlyFollows
            .filterKeys { it.target != start }
            .entries
            .groupingBy { it.key.target }
            .aggregate { key, accumulator: Double?, element, first ->
                if (first) element.value else max(accumulator!!, element.value)
            }.values.filterNotNull().min()!!

        println("directlyFollowsTh=$directlyFollowsTh")
    }


    override fun computeDependencyGraph(): Collection<Dependency> =
        directlyFollows
            .filterValues { it >= directlyFollowsTh }
            .keys

}