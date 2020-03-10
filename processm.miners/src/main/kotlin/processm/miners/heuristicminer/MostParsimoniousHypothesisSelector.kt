package processm.miners.heuristicminer

/**
 * Selects the replay trace with the minimal number of used dependencies.
 */
class MostParsimoniousHypothesisSelector : CountingHypothesisSelector() {
    override fun invoke(currentStates: Collection<ReplayTrace>): ReplayTrace {
        return super.invoke(currentStates) { min() }
    }
}