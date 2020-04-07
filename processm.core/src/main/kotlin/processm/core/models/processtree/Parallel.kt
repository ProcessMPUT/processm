package processm.core.models.processtree

import processm.core.models.processtree.execution.ExecutionNode
import processm.core.models.processtree.execution.ParallelExecution

class Parallel(vararg nodes: Node) : InternalNode(*nodes) {
    override val symbol: String
        get() = "∧"

    override val startActivities: kotlin.sequences.Sequence<Activity>
        get() = children.asSequence().flatMap { it.startActivities }

    override val endActivities: kotlin.sequences.Sequence<Activity>
        get() = children.asSequence().flatMap { it.endActivities }

    override fun executionNode(parent: ExecutionNode?): ExecutionNode = ParallelExecution(this, parent)

    override val isStrict: Boolean = false
}