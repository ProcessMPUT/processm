package processm.core.models.processtree.execution

import processm.core.models.commons.ProcessModelState
import processm.core.models.processtree.ProcessTreeActivity
import processm.core.models.processtree.Sequence
import processm.helpers.ifNullOrEmpty

/**
 * An [ExecutionNode] for [Sequence].
 */
class SequenceExecution(
    override val base: Sequence,
    parent: ExecutionNode?,
    current: ExecutionNode? = null,
    cause: Collection<ProcessTreeActivity> = parent?.lastExecuted.ifNullOrEmpty { parent?.cause.orEmpty() }
) : ExecutionNode(base, parent, cause) {

    private var index = 0
    private var current = current ?: base.children[index].executionNode(this)

    override val available
        get() = if (!isComplete) current.available else emptySequence()

    override var isComplete: Boolean = false
        private set

    override var lastExecuted: Collection<ProcessTreeActivity> = emptyList()
        private set

    override fun postExecution(child: ExecutionNode) {
        require(child.parent === this)
        lastExecuted = current.lastExecuted

        if (child.isComplete) {
            if (index + 1 < base.children.size) {
                current = base.children[++index].executionNode(this)
                isComplete = false
            } else {
                isComplete = true
            }
        }
        parent?.postExecution(this)
    }

    override fun copy(): ProcessModelState =
        SequenceExecution(base, parent, this.current.copy() as ExecutionNode, cause).also {
            it.index = this.index
            it.isComplete = this.isComplete
            it.lastExecuted = this.lastExecuted
            it.current.parent = it
        }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as SequenceExecution

        if (base != other.base) return false
        if (index != other.index) return false
        if (current != other.current) return false

        return true
    }

    override fun hashCode(): Int {
        var result = base.hashCode()
        result = 31 * result + index
        result = 31 * result + current.hashCode()
        return result
    }
}
