package processm.core.models.processtree.execution

import processm.core.models.commons.ActivityExecution
import processm.core.models.commons.ProcessModelState
import processm.core.models.processtree.ProcessTreeActivity
import processm.helpers.ifNullOrEmpty

/**
 * [ExecutionNode] for an [ProcessTreeActivity]
 */
class ActivityExecution(
    override val base: ProcessTreeActivity,
    parent: ExecutionNode?,
    cause: Collection<ProcessTreeActivity> = parent?.lastExecuted.ifNullOrEmpty { parent?.cause.orEmpty() }
) : ExecutionNode(base, parent, cause),
    ActivityExecution {

    override val available
        get() = if (!isComplete) sequenceOf(this) else emptySequence()

    override var isComplete: Boolean = false
        private set

    override val lastExecuted: Collection<ProcessTreeActivity>
        get() = if (isComplete) listOf(base) else emptyList()

    override fun postExecution(child: ExecutionNode) =
        throw UnsupportedOperationException("An activity cannot have children")

    override val activity: ProcessTreeActivity = base

    override fun execute() {
        isComplete = true
        parent?.postExecution(this)
    }

    override fun copy(): ProcessModelState = ActivityExecution(base, parent, cause).also {
        it.isComplete = this.isComplete
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as processm.core.models.processtree.execution.ActivityExecution

        if (isComplete != other.isComplete) return false
        if (activity != other.activity) return false

        return true
    }

    override fun hashCode(): Int {
        var result = isComplete.hashCode()
        result = 31 * result + activity.hashCode()
        return result
    }


}
