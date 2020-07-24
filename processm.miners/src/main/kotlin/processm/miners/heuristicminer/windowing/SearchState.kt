package processm.miners.heuristicminer.windowing

import processm.miners.heuristicminer.NodeTrace
import processm.miners.heuristicminer.ReplayTrace

data class SearchState(
    val totalGreediness: Double,
    val solutionLength: Int,
    val node: Int,
    val produce: Boolean,
    val trace: ReplayTrace,
    val nodeTrace: NodeTrace
) : HasFeatures() {

    override val features: List<Double>
    //    get() = listOf(-totalGreediness / solutionLength, -node.toDouble())
        get() = listOf(-(totalGreediness + (2*nodeTrace.size-2-solutionLength)) , -node.toDouble())
}