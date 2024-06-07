package processm.core.models.processtree.execution

import processm.core.models.commons.ProcessModelState
import processm.core.models.processtree.Node
import processm.core.models.processtree.ProcessTreeActivity
import processm.helpers.ifNullOrEmpty

/**
 * A node of the tree-like state of execution for [processm.core.models.processtree.ProcessTree]. [ExecutionNode]s
 * correspond one-to-one to the nodes of the process tree.
 *
 * @property base The corresponding process tree node.
 * @property parent The parent execution node.
 * @property cause The activities which execution led directly to the current process state. Empty collection for the
 * first activity (or activities) in the process. May contain many activities if parallel execution led to the current state.
 */
abstract class ExecutionNode(
    open val base: Node,
    internal var parent: ExecutionNode?,
    val cause: Array<out ProcessTreeActivity> = parent?.lastExecuted.ifNullOrEmpty { parent?.cause.orEmpty() }
) : ProcessModelState {
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

    /**
     * The collection of activities in the [base] subtree that were executed last and are the direct causes for the next
     * executing activity. This collection consists of at most one activity unless the [base] subtree consists of
     * [processm.core.models.processtree.Parallel] node. This property is mutable and represents the current state of
     * execution.
     */
    abstract val lastExecuted: Array<ProcessTreeActivity>
}
