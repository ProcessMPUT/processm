package processm.core.models.processtree.execution

import processm.core.models.commons.ProcessModelState
import processm.core.models.processtree.Parallel
import processm.core.models.processtree.ProcessTreeActivity
import processm.helpers.flatMapToArray
import processm.helpers.ifNullOrEmpty
import processm.helpers.mapToArray

/**
 * An [ExecutionNode] for [Parallel]
 */
class ParallelExecution(
    override val base: Parallel,
    parent: ExecutionNode?,
    children: Array<out ExecutionNode>? = null,
    cause: Array<out ProcessTreeActivity> = parent?.lastExecuted.ifNullOrEmpty { parent?.cause.orEmpty() }
) : ExecutionNode(base, parent, cause) {

    private val children = children ?: base.children.mapToArray { it.executionNode(this) }

    /**
     * Relies on children returning empty [available] if they are complete
     */
    override val available
        get() = children.asSequence().flatMap { it.available }

    override var isComplete: Boolean = false
        private set

    // // The initialization of the children property calls executionNode() that may read lastExecuted property when the children property has been not initialized yet
    override val lastExecuted: Array<out ProcessTreeActivity>
        get() = children.orEmpty().flatMapToArray { it.lastExecuted }

    override fun postExecution(child: ExecutionNode) {
        require(child.parent === this)
        if (child.isComplete)
            isComplete = children.all { it.isComplete }
        parent?.postExecution(this)
    }

    override fun copy(): ProcessModelState {
        val childrenCopy = children.mapToArray { child -> child.copy() as ExecutionNode }
        return ParallelExecution(
            base,
            parent,
            childrenCopy,
            cause
        ).also {
            isComplete = this.isComplete
            childrenCopy.forEach { child -> child.parent = it }
        }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as ParallelExecution

        if (base != other.base) return false
        if (isComplete != other.isComplete) return false
        if (!children.contentEquals(other.children)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = base.hashCode()
        result = 31 * result + isComplete.hashCode()
        result = 31 * result + children.size
        return result
    }
}
