package processm.miners.heuristicminer.hypothesisselector

import processm.miners.heuristicminer.ReplayTrace

/**
 * Selects "the best" (for any definition of best) replay trace of all possible replay traces of a given trace
 */
interface ReplayTraceHypothesisSelector {
    operator fun invoke(currentStates: Collection<ReplayTrace>): ReplayTrace
}