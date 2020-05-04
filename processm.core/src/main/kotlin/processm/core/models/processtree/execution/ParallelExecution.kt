package processm.core.models.processtree.execution

import processm.core.models.processtree.Parallel

/**
 * An [ExecutionNode] for [Parallel]
 */
class ParallelExecution(override val base: Parallel, parent: ExecutionNode?) : ExecutionNode(base, parent) {

    private val children = base.children.map { it.executionNode(this) }.asSequence()

    /**
     * Relies on children returning empty [available] if they are complete
     */
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