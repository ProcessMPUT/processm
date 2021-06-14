package processm.experimental.heuristicminer.dependencygraphproviders

import processm.core.models.causalnet.Dependency
import processm.core.models.causalnet.Node

/**
 * [DependencyGraphProvider] using a dependency measure, like a vanilla heuristic miner
 */
open class DefaultDependencyGraphProvider(
    minDirectlyFollows: Int,
    protected val minDependency: Double
) : BasicDependencyGraphProvider(minDirectlyFollows) {

    internal fun dependency(a: Node, b: Node): Double =
        if (a != b) {
            val ab = directlyFollows.getOrDefault(Dependency(a, b), 0)
            val ba = directlyFollows.getOrDefault(Dependency(b, a), 0)
            (ab - ba) / (ab + ba + 1.0)
        } else {
            val aa = directlyFollows.getOrDefault(Dependency(a, a), 0)
            aa / (aa + 1.0)
        }


    override fun computeDependencyGraph() =
        super.computeDependencyGraph().filter { (a, b) -> dependency(a, b) >= minDependency }
}