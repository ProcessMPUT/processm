package processm.core.models.processtree

import processm.core.models.commons.Activity
import processm.core.models.commons.ControlStructureType
import processm.core.models.processtree.execution.ExecutionNode
import processm.core.models.processtree.execution.SequenceExecution

/**
 * A sequential composition. This node executes children from the first to the last, in order.
 */
class Sequence(vararg nodes: Node) : InternalNode(*nodes) {
    override val symbol: String
        get() = "→"
    override val startActivities: kotlin.sequences.Sequence<ProcessTreeActivity>
        get() = children.first().startActivities

    override val endActivities: kotlin.sequences.Sequence<ProcessTreeActivity>
        get() = children.last().endActivities

    override val type: ControlStructureType
        get() = ControlStructureType.Causality

    override val controlFlowComplexity: Int
        get() = 1

    override fun executionNode(parent: ExecutionNode?): SequenceExecution = SequenceExecution(this, parent)

    override fun getLastActivitiesInSubtree() = childrenInternal.last().getLastActivitiesInSubtree()

    override fun getPrecedingActivities(childNode: Node): Collection<Activity> {
        val childNodeIndex = childrenInternal.indexOf(childNode)

        return if (childNodeIndex < 0) throw IllegalCallerException("$childNode is not child of $this and should not call")
        else if (childNodeIndex == 0) getPrecedingActivitiesFromParent()
        else childrenInternal[childNodeIndex - 1].getLastActivitiesInSubtree()
    }
}
