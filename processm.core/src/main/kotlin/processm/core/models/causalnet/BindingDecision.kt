package processm.core.models.causalnet

import processm.core.models.commons.Decision

/**
 * A decision resulting in chosing [binding] in [decisionPoint]
 */
data class BindingDecision(val binding: Binding?, override val decisionPoint: DecisionPoint) : Decision