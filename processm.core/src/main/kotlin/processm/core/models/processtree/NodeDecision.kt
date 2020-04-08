package processm.core.models.processtree

import processm.core.models.commons.AbstractDecision

/**
 * Execute [node] as the decision in [decisionPoint]. A decision in a process tree always selects a single node to execute.
 */
data class NodeDecision(val node: Node, override val decisionPoint: InternalNode) : AbstractDecision {
}