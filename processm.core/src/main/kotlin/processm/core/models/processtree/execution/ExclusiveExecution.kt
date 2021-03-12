package processm.core.models.processtree.execution

import processm.core.models.commons.ProcessModelState
import processm.core.models.processtree.Exclusive

/**
 * An [ExecutionNode] for [Exclusive]
 */
class ExclusiveExecution(override val base: Exclusive, parent: ExecutionNode?) : ExecutionNode(base, parent) {

    private var selected: ExecutionNode? = null
    private val children = base.children.map { it.executionNode(this) }

    override val available
        get() = if (!isComplete) {
            if (selected != null)
                selected!!.available
            else
                children.asSequence().flatMap { it.available }
        } else emptySequence()

    override var isComplete: Boolean = false
        private set

    override fun postExecution(child: ExecutionNode) {
        require(child.parent === this)
        selected = child
        if (child.isComplete)
            isComplete = true
        parent?.postExecution(this)
    }

    override fun copy(): ProcessModelState = ExclusiveExecution(base, parent).also {
        it.selected = this.selected?.copy() as ExecutionNode?
        it.selected?.parent = it
        it.isComplete = this.isComplete
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as ExclusiveExecution

        if (base != other.base) return false
        if (selected != other.selected) return false
        if (isComplete != other.isComplete) return false
        if (children != other.children) return false

        return true
    }

    override fun hashCode(): Int {
        var result = base.hashCode()
        result = 31 * result + (selected?.hashCode() ?: 0)
        result = 31 * result + isComplete.hashCode()
        result = 31 * result + children.size
        return result
    }
}
