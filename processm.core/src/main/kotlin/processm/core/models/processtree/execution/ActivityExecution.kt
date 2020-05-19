package processm.core.models.processtree.execution

import processm.core.models.commons.ActivityExecution
import processm.core.models.processtree.ProcessTreeActivity

/**
 * [ExecutionNode] for an [ProcessTreeActivity]
 */
class ActivityExecution(override val base: ProcessTreeActivity, parent: ExecutionNode?) : ExecutionNode(base, parent),
        ActivityExecution {

    override val available
        get() = if (!isComplete) sequenceOf(this) else emptySequence()

    override var isComplete: Boolean = false
        private set

    override fun postExecution(child: ExecutionNode) =
        throw UnsupportedOperationException("An activity cannot have children")

    override val activity: ProcessTreeActivity = base

    override fun execute() {
        isComplete = true
        parent?.postExecution(this)
    }
}