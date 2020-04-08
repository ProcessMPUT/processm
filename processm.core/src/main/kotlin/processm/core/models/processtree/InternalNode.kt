package processm.core.models.processtree

import processm.core.models.commons.AbstractDecisionPoint

/**
 * A base class for all nodes that represent a decision (i.e., internal nodes of a tree) rather than an activity (i.e., leafs of a tree)
 *
 * Strict decisions in a process tree are possible only in [Exclusive] and [RedoLoop]
 */
abstract class InternalNode(vararg nodes: Node) : Node(*nodes), AbstractDecisionPoint {
    override val possibleOutcomes: List<NodeDecision> = emptyList()
}