package processm.miners.heuristicminer

/**
 * Selects "the best" (for any definition of best) replay trace of all possible replay traces of a given trace
 */
interface ReplayTraceHypothesisSelector {
    operator fun invoke(currentStates: Collection<ReplayTrace>): ReplayTrace
}