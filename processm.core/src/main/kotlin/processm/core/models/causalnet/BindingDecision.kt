package processm.core.models.causalnet

import processm.core.models.commons.AbstractDecision

/**
 * A decision resulting in chosing [binding] in [decisionPoint]
 */
data class BindingDecision(val binding: Binding?, override val decisionPoint: DecisionPoint) : AbstractDecision