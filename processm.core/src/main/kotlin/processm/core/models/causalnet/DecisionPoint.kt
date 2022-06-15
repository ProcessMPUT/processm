package processm.core.models.causalnet

import processm.core.models.commons.Activity
import processm.core.models.commons.DecisionPoint

/**
 * A CausalNet decision point, i.e., choice of a join or a split for the give [node]
 */
data class DecisionPoint(val node: Node, private val bindings: Set<Binding>, override val previousActivities: Collection<Activity> = emptySet()) : DecisionPoint {
    override val possibleOutcomes: List<BindingDecision> = bindings.map { BindingDecision(it, this) }
}