package processm.core.models.processtree

import processm.core.models.commons.ControlStructureType
import processm.core.models.processtree.execution.ExecutionNode
import processm.core.models.processtree.execution.RedoLoopExecution

/**
 * A redo loop composition that first executes the leftmost child, then a decision is made whether execute exactly one
 * of the other children and the first one again, or exit the loop.
 */
class RedoLoop(vararg nodes: Node) : InternalNode(*nodes) {
    override val symbol: String
        get() = "⟲"

    override val startActivities: kotlin.sequences.Sequence<ProcessTreeActivity>
        get() = children[0].startActivities

    override val endActivities: kotlin.sequences.Sequence<ProcessTreeActivity>
        get() = children[0].endActivities

    override val type: ControlStructureType
        get() = ControlStructureType.OtherSplit

    override val controlFlowComplexity: Int
        get() = children.size

    override fun executionNode(parent: ExecutionNode?): RedoLoopExecution = RedoLoopExecution(this, parent)

    val endLoopActivity = EndLoopSilentActivity(this)

    override val possibleOutcomes: List<NodeDecision> by lazy(LazyThreadSafetyMode.NONE) {
        check(children.size >= 2) { "RedoLoop should contain at least two nodes!" }

        listOf(NodeDecision(endLoopActivity, this)) + children.subList(1, children.size).map { NodeDecision(it, this) }
    }
}
