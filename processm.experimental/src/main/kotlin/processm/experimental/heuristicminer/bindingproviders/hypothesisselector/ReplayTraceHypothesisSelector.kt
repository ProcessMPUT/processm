package processm.experimental.heuristicminer.bindingproviders.hypothesisselector

import processm.miners.onlineminer.ReplayTrace

/**
 * Selects "the best" (for any definition of best) replay trace of all possible replay traces of a given trace
 */
interface ReplayTraceHypothesisSelector {
    operator fun invoke(currentStates: Collection<ReplayTrace>): ReplayTrace
}