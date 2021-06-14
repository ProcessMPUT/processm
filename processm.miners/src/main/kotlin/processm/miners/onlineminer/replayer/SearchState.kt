package processm.miners.onlineminer.replayer

import org.apache.commons.math3.fraction.BigFraction
import processm.miners.onlineminer.HasFeatures
import processm.miners.onlineminer.minus
import processm.miners.onlineminer.plus

internal data class SearchState(
    val totalGreediness: BigFraction,
    val heuristicPenalty: BigFraction,
    val solutionLength: Int,
    val node: Int,
    val produce: Boolean,
    val trace: ReplayTrace
) : HasFeatures() {

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