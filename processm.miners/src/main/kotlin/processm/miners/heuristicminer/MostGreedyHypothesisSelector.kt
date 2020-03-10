package processm.miners.heuristicminer

/**
 * Selects the replay trace with the maximal number of used dependencies.
 */
class MostGreedyHypothesisSelector : CountingHypothesisSelector() {
    override fun invoke(currentStates: Collection<ReplayTrace>): ReplayTrace {
        return super.invoke(currentStates) { max() }
    }
}