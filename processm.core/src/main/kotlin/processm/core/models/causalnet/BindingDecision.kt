package processm.core.models.causalnet

import processm.core.models.commons.AbstractDecision

data class BindingDecision(val binding: Binding?, override val decisionPoint: DecisionPoint) : AbstractDecision {
}