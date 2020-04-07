package processm.core.models.processtree.execution

import processm.core.models.processtree.EndLoopSilentActivity
import processm.core.models.processtree.RedoLoop

class RedoLoopExecution(override val base: RedoLoop, parent: ExecutionNode?) : ExecutionNode(base, parent) {

    private var doPhase = true

    private val redoPhase: Boolean
        get() = !doPhase

    private var current: ExecutionNode? = base.children[0].executionNode(this)

    private val completionActivity = EndLoopSilentActivity(base).executionNode(this)

    init {
        completionActivity.base.parent = base
    }

    override val available
        get() = if (!isComplete) {
            if (current != null)
                current!!.available
            else {
                assert(redoPhase)
                sequenceOf(completionActivity) + base.children.subList(1, base.children.size).asSequence()
                    .flatMap { it.executionNode(this).available }
            }
        } else emptySequence()

    override var isComplete: Boolean = false
        private set

    override fun postExecution(child: ExecutionNode) {
        require(child.parent === this)
        require(current === null || child === current)
        if (child === completionActivity) {
            check(redoPhase)
            isComplete = true
        }
        if (child.isComplete) {
            doPhase = !doPhase
            current = if (doPhase)
                base.children[0].executionNode(this)
            else
                null
        } else {
            current = child
        }
        parent?.postExecution(this)
    }


}