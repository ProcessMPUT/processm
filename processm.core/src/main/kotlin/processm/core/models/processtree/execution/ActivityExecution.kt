package processm.core.models.processtree.execution

import processm.core.models.commons.AbstractActivityExecution
import processm.core.models.processtree.Activity

/**
 * [ExecutionNode] for an [Activity]
 */
class ActivityExecution(override val base: Activity, parent: ExecutionNode?) : ExecutionNode(base, parent),
    AbstractActivityExecution {

    override val available
        get() = if (!isComplete) sequenceOf(this) else emptySequence()

    override var isComplete: Boolean = false
        private set

    override fun postExecution(child: ExecutionNode) =
        throw UnsupportedOperationException("An activity cannot have children")

    override val activity: Activity = base

    override fun execute() {
        isComplete = true
        parent?.postExecution(this)
    }
}