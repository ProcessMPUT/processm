package processm.miners.heuristicminer.windowing

import org.apache.commons.math3.fraction.BigFraction
import processm.miners.heuristicminer.ReplayTrace

data class SearchState(
    val totalGreediness: BigFraction,
    val heuristicPenalty: BigFraction,
    val solutionLength: Int,
    val node: Int,
    val produce: Boolean,
    val trace: ReplayTrace
) : HasFeatures() {

    /*
    cost = -gain
    gain = totalGreediness + expectedGreediness
    expectedGreediness = 1 for each undecided binding = #bindings - solutionLength
    expectedGreediness is the best we can do, i.e., we are never underestimating the gain, i.e., we are never overestimating the cost
    #bindings = 2*trace.size - 2 (there are 2 bindings for each node except for start and end)

    Observe that 2*trace.size is a constant so we can simplify:
    cost = -(totalGreediness - solutionLength) = solutionLength - totalGreediness
    I.e., the cost is the difference between maximal greediness and actual greediness and the heuristic is 0 (perfect greediness in the future)
    In general, we cannot do better w.r.t. the heuristic, as perfect greediness is a possible solution.

    On further reflection, we are able to detect that a perfect solution is impossible and give an lower bound on penalty
     */

    override val features: List<BigFraction> by lazy(LazyThreadSafetyMode.NONE) {
        listOf(
            (BigFraction(solutionLength, 1) - totalGreediness + heuristicPenalty).reduce(),
            BigFraction(-node, 1),
            heuristicPenalty
        )
    }

    val debugInfo: String
        get() = "sL - tG + hP = $solutionLength - $totalGreediness + $heuristicPenalty; features = $features"
}