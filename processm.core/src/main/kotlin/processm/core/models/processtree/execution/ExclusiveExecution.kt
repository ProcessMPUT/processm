package processm.core.models.processtree.execution

import processm.core.models.processtree.Exclusive

class ExclusiveExecution(override val base: Exclusive, parent: ExecutionNode?) : ExecutionNode(base, parent) {

    private var selected: ExecutionNode? = null
    private val children = base.children.map { it.executionNode(this) }.asSequence()

    override val available
        get() = if (!isComplete) {
            if (selected != null)
                selected!!.available
            else
                children.flatMap { it.available }
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


}