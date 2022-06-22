package processm.core.models.commons

/**
 * A common interface for decision points of a model.
 */
interface DecisionPoint {

    /**
     * A collection of activities that lead to the decision point. Not all activities are required to be executed.
     */
    val previousActivities: Collection<Activity>
        get() = emptySet()

    /**
     * A collection of possible outcomes of this decision point. This is an exclusive choice, i.e., exactly one of these decisions should be made.
     */
    val possibleOutcomes: Collection<Decision>

    /**
     * True if there is real decision to make here, e.g., an exclusive choice with more than one possible outcome
     */
    val isRealDecision: Boolean
        get() = possibleOutcomes.size > 1
}