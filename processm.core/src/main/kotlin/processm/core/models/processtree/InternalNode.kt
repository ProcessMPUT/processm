package processm.core.models.processtree

import processm.core.models.commons.AbstractDecisionPoint

/**
 * A base class for all nodes that are represent a decision (i.e., internal nodes of a tree) rather than an activity (i.e., leafs of a tree)
 */
abstract class InternalNode(vararg nodes: Node) : Node(*nodes), AbstractDecisionPoint