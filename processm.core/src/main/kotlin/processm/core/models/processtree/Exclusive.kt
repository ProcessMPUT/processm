package processm.core.models.processtree

import processm.core.models.commons.ControlStructureType
import processm.core.models.processtree.execution.ExclusiveExecution
import processm.core.models.processtree.execution.ExecutionNode

/**
 * An exclusive-OR composition that executes exactly one child.
 */
class Exclusive(vararg nodes: Node) : InternalNode(*nodes) {
    override val symbol: String
        get() = "×"

    override val startActivities: kotlin.sequences.Sequence<ProcessTreeActivity>
        get() = children.asSequence().flatMap { it.startActivities }

    override val endActivities: kotlin.sequences.Sequence<ProcessTreeActivity>
        get() = children.asSequence().flatMap { it.endActivities }

    override val type: ControlStructureType
        get() = ControlStructureType.XorSplit

    override val controlFlowComplexity: Int
        get() = children.size

    override fun executionNode(parent: ExecutionNode?): ExclusiveExecution = ExclusiveExecution(this, parent)

    override val possibleOutcomes = children.map { NodeDecision(it, this) }
}
