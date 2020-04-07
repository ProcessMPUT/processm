package processm.core.models.processtree

import processm.core.models.processtree.execution.ExecutionNode
import processm.core.models.processtree.execution.SequenceExecution

class Sequence(vararg nodes: Node) : InternalNode(*nodes) {
    override val symbol: String
        get() = "→"
    override val startActivities: kotlin.sequences.Sequence<Activity>
        get() = children[0].startActivities

    override val endActivities: kotlin.sequences.Sequence<Activity>
        get() = children.last().endActivities

    override fun executionNode(parent: ExecutionNode?): SequenceExecution = SequenceExecution(this, parent)

    override val isStrict: Boolean = false
}