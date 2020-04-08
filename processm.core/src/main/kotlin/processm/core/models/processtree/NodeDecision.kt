package processm.core.models.processtree

import processm.core.models.commons.AbstractDecision

data class NodeDecision(val node: Node, override val decisionPoint: InternalNode) : AbstractDecision {
}