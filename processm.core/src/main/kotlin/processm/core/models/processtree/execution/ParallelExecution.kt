package processm.core.models.processtree.execution

import processm.core.models.commons.ProcessModelState
import processm.core.models.processtree.Parallel

/**
 * An [ExecutionNode] for [Parallel]
 */
class ParallelExecution(override val base: Parallel, parent: ExecutionNode?) : ExecutionNode(base, parent) {

    private var children = base.children.map { it.executionNode(this) }

    /**
     * Relies on children returning empty [available] if they are complete
     */
    override val available
        get() = children.asSequence().flatMap { it.available }

    override var isComplete: Boolean = false
        private set

    override fun postExecution(child: ExecutionNode) {
        require(child.parent === this)
        if (child.isComplete)
            isComplete = children.all { it.isComplete }
        parent?.postExecution(this)
    }

    override fun copy(): ProcessModelState = ParallelExecution(base, parent).also {
        it.isComplete = this.isComplete
        it.children = this.children.map { child -> (child.copy() as ExecutionNode).apply { parent = it } }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as ParallelExecution

        if (base != other.base) return false
        if (isComplete != other.isComplete) return false
        if (children != other.children) return false

        return true
    }

    override fun hashCode(): Int {
        var result = base.hashCode()
        result = 31 * result + isComplete.hashCode()
        result = 31 * result + children.size
        return result
    }
}
