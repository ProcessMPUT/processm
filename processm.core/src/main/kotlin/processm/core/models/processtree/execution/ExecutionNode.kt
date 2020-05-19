package processm.core.models.processtree.execution

import processm.core.models.commons.ProcessModelState
import processm.core.models.processtree.Node

abstract class ExecutionNode(open val base: Node, internal val parent: ExecutionNode?) : ProcessModelState {
    /**
     * Activities (possibly silent) that can be currently executed in the process tree rooted at [base].
     * Empty if [isComplete] is true
     */
    abstract val available: Sequence<ActivityExecution>

    /**
     * True if there are no activities to execute in this node
     */
    abstract val isComplete: Boolean

    /**
     * A hook propagation information about the execution of some descendant up a tree.
     * Call to [parent]'s [postExecution] is supposed to be the last thing to do
     */
    internal abstract fun postExecution(child: ExecutionNode)
}