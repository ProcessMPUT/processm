package processm.core.models.causalnet

import processm.core.models.commons.AbstractDecisionPoint

data class DecisionPoint(val node: Node, val bindings: Set<Binding>) : AbstractDecisionPoint {
    override val isStrict: Boolean = bindings.size > 1
}