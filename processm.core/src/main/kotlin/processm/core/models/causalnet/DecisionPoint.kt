package processm.core.models.causalnet

import processm.core.models.commons.AbstractDecisionPoint

class DecisionPoint(val node: Node, bindings: Set<Binding>) : AbstractDecisionPoint {
    override val possibleOutcomes: List<BindingDecision> = bindings.map { BindingDecision(it, this) }
    override val isStrict: Boolean = possibleOutcomes.size > 1
}