package processm.core.models.processtree

import processm.core.models.commons.ControlStructureType
import processm.core.models.processtree.execution.ExecutionNode
import processm.core.models.processtree.execution.ParallelExecution

/**
 * A parallel composition that executes all children in any order, possibly concurrently.
 */
class Parallel(vararg nodes: Node) : InternalNode(*nodes) {
    override val symbol: String
        get() = "∧"

    override val startActivities: kotlin.sequences.Sequence<ProcessTreeActivity>
        get() = children.asSequence().flatMap { it.startActivities }

    override val endActivities: kotlin.sequences.Sequence<ProcessTreeActivity>
        get() = children.asSequence().flatMap { it.endActivities }

    override val type: ControlStructureType
        get() = ControlStructureType.AndSplit

    override val controlFlowComplexity: Int
        get() = 1

    override fun executionNode(parent: ExecutionNode?): ExecutionNode = ParallelExecution(this, parent)
}
