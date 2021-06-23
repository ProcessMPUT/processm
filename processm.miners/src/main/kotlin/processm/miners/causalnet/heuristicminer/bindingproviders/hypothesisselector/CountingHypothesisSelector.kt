package processm.miners.causalnet.heuristicminer.bindingproviders.hypothesisselector

import processm.core.logging.logger
import processm.miners.causalnet.onlineminer.replayer.ReplayTrace

/**
 * Selects the best replay trace by counting overall number of used dependencies
 */
abstract class CountingHypothesisSelector :
    ReplayTraceHypothesisSelector {
    fun invoke(currentStates: Collection<ReplayTrace>, crit: Collection<Int>.() -> Int?): ReplayTrace {
        val statesWithCounts = currentStates
            .map { trace ->
                trace to trace.joins.flatten().size + trace.splits.flatten().size
            }
        val best = statesWithCounts.map { (k, v) -> v }.crit()
        val result = statesWithCounts
            .filter { (k, v) -> v == best }
            .map { (k, v) -> k }
        if (result.size != 1) {
            logger().warn("Multiple equally good hypotheses, picking first")
        }
        return result.first()
    }
}