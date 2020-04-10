package processm.core.models.causalnet

import processm.core.models.commons.AbstractDecisionPoint

/**
 * A CausalNet decision point, i.e., choice of a join or a split for the give [node]
 */
data class DecisionPoint(val node: Node, private val bindings: Set<Binding>) : AbstractDecisionPoint {
    override val possibleOutcomes: List<BindingDecision> = bindings.map { BindingDecision(it, this) }
}