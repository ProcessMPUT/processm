package processm.miners.causalnet.heuristicminer.dependencygraphproviders

import processm.core.models.causalnet.Dependency
import processm.core.models.causalnet.Node
import processm.helpers.Counter
import processm.miners.causalnet.onlineminer.NodeTrace

/**
 * [DefaultDependencyGraphProvider] plus additional measure to detect L2 loops from Flexible Heuristic Miner
 */
open class L2DependencyGraphProvider(
    minDirectlyFollows: Int,
    minDependency: Double,
    protected val minL2: Double
) : DefaultDependencyGraphProvider(minDirectlyFollows, minDependency) {

    /**
     * For a dependency a->b (a!=b), number of times a->b->a occurred
     */
    protected val l2Loops = Counter<Dependency>()

    override fun processTrace(nodeTrace: NodeTrace) {
        super.processTrace(nodeTrace)
        val i = nodeTrace.iterator()
        var prev = start
        var prev2: Node? = null
        while (i.hasNext()) {
            val curr = i.next()
            if (prev2 == curr && prev2 != prev)
                l2Loops.inc(Dependency(prev2, prev))
            prev2 = prev
            prev = curr
        }
    }


    internal fun dependency2(a: Node, b: Node): Double {
        val x = l2Loops[Dependency(a, b)].toDouble()
        val y = l2Loops[Dependency(b, a)].toDouble()
        return (x + y) / (x + y + 1)
    }

    override fun computeDependencyGraph(): MutableMap<Dependency, Double> {
        val result = super.computeDependencyGraph()
        for (dep in l2Loops.keys) {
            val measure = dependency2(dep.source, dep.target)
            if (measure >= minL2) {
                result[dep] = measure
                result[Dependency(dep.target, dep.source)] = measure
            }
        }
        return result
    }

}
