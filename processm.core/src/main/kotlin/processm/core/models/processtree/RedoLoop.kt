package processm.core.models.processtree

import processm.core.models.processtree.execution.ExecutionNode
import processm.core.models.processtree.execution.RedoLoopExecution

class RedoLoop(vararg nodes: Node) : InternalNode(*nodes) {
    override val symbol: String
        get() = "⟲"
    override val startActivities: kotlin.sequences.Sequence<Activity>
        get() = children[0].startActivities

    override val endActivities: kotlin.sequences.Sequence<Activity>
        get() = children[0].endActivities

    override fun executionNode(parent: ExecutionNode?): RedoLoopExecution = RedoLoopExecution(this, parent)

    override val isStrict: Boolean = true
}