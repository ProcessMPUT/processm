package processm.core.models.processtree.execution

import processm.core.models.processtree.Parallel

class ParallelExecution(override val base: Parallel, parent: ExecutionNode?) : ExecutionNode(base, parent) {

    private val children = base.children.map { it.executionNode(this) }.asSequence()

    override val available
        get() = children.flatMap { it.available }

    override var isComplete: Boolean = false
        private set

    override fun postExecution(child: ExecutionNode) {
        require(child.parent === this)
        if (child.isComplete)
            isComplete = children.all { it.isComplete }
        parent?.postExecution(this)
    }

}