package processm.core.models.processtree.execution

import processm.core.models.processtree.Sequence

class SequenceExecution(override val base: Sequence, parent: ExecutionNode?) : ExecutionNode(base, parent) {

    private var iter = base.children.iterator()
    private var current = iter.next().executionNode(this)

    override val available
        get() = if (!isComplete) current.available else emptySequence()

    override var isComplete: Boolean = false
        private set

    override fun postExecution(child: ExecutionNode) {
        require(child.parent === this)
        if (child.isComplete) {
            if (iter.hasNext()) {
                current = iter.next().executionNode(this)
                isComplete = false
            } else
                isComplete = true
        }
        parent?.postExecution(this)
    }

}