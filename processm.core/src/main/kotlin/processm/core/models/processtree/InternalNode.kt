package processm.core.models.processtree

import processm.core.models.commons.Activity
import processm.core.models.commons.DecisionPoint

/**
 * A base class for all nodes that represent a decision (i.e., internal nodes of a tree) rather than an activity (i.e., leafs of a tree)
 *
 * Strict decisions in a process tree are possible only in [Exclusive] and [RedoLoop]
 */
abstract class InternalNode(vararg nodes: Node) : Node(*nodes), DecisionPoint {
    override val possibleOutcomes: List<NodeDecision> = emptyList()

    override val previousActivities: Collection<Activity> get() = getPrecedingActivitiesFromParent()

    protected fun getPrecedingActivitiesFromParent() =
        (parent as? InternalNode)?.getPrecedingActivities(this) ?: emptySet()

    override fun getLastActivitiesInSubtree(): Collection<Activity> = childrenInternal.flatMap { it.getLastActivitiesInSubtree() }

    protected open fun getPrecedingActivities(childNode: Node): Collection<Activity> = getPrecedingActivitiesFromParent()
}