package processm.core.models.processtree

import processm.core.models.processtree.execution.ExecutionNode
import processm.core.models.processtree.execution.RedoLoopExecution

class RedoLoop(vararg nodes: Node) : InternalNode(*nodes) {
    override val symbol: String
        get() = "⟲"
    override val startActivities: kotlin.sequences.Sequence<ProcessTreeActivity>
        get() = children[0].startActivities

    override val endActivities: kotlin.sequences.Sequence<ProcessTreeActivity>
        get() = children[0].endActivities

    override fun executionNode(parent: ExecutionNode?): RedoLoopExecution = RedoLoopExecution(this, parent)

    val endLoopActivity = EndLoopSilentActivity(this)

    override val possibleOutcomes: List<NodeDecision> by lazy(LazyThreadSafetyMode.NONE) {
        check(children.size >= 2) { "RedoLoop should contain at least two nodes!" }
        
        listOf(NodeDecision(endLoopActivity, this)) + children.subList(1, children.size).map { NodeDecision(it, this) }
    }
}