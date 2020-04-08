package processm.core.models.commons

/**
 * A common interface for decision points of a model.
 */
interface AbstractDecisionPoint {

    /**
     * A collection of possible outcomes of this decision point. This is an exclusive choice, i.e., exactly one of these decisions should be made.
     */
    val possibleOutcomes: Collection<AbstractDecision>

    /**
     * True if there is real decision to make here, e.g., an exclusive choice with more than one possible outcome
     */
    val isStrict: Boolean
        get() = possibleOutcomes.size > 1
}