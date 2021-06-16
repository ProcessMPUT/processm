package processm.miners.causalnet.onlineminer.replayer

import org.apache.commons.math3.fraction.BigFraction
import processm.miners.causalnet.onlineminer.HasFeatures
import processm.miners.causalnet.onlineminer.minus
import processm.miners.causalnet.onlineminer.plus

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