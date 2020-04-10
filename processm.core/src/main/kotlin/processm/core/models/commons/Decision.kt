package processm.core.models.commons

/**
 * A base interface for a decision that was or could be made in [decisionPoint]
 */
interface Decision {
    val decisionPoint: DecisionPoint
}