package processm.core.models.petrinet

import processm.core.models.commons.Decision
import processm.core.models.commons.DecisionPoint

/**
 * A result of decision making in a Petri net.
 *
 * @property transition The transition being the result of making a decision.
 */
data class TransitionDecision(
    val transition: Transition,
    override val decisionPoint: DecisionPoint
) : Decision
