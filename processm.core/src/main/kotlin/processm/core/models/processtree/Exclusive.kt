package processm.core.models.processtree

import processm.core.models.processtree.execution.ExclusiveExecution
import processm.core.models.processtree.execution.ExecutionNode

class Exclusive(vararg nodes: Node) : InternalNode(*nodes) {
    override val symbol: String
        get() = "×"

    override val startActivities: kotlin.sequences.Sequence<Activity>
        get() = children.asSequence().flatMap { it.startActivities }

    override val endActivities: kotlin.sequences.Sequence<Activity>
        get() = children.asSequence().flatMap { it.endActivities }

    override fun executionNode(parent: ExecutionNode?): ExclusiveExecution = ExclusiveExecution(this, parent)

    override val isStrict: Boolean = children.size > 1
}