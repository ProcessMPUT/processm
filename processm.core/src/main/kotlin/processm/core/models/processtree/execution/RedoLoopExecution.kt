package processm.core.models.processtree.execution

import processm.core.models.commons.ProcessModelState
import processm.core.models.processtree.ProcessTreeActivity
import processm.core.models.processtree.RedoLoop
import processm.helpers.ifNullOrEmpty

/**
 * An [ExecutionNode] for [RedoLoop]
 */
class RedoLoopExecution(
    override val base: RedoLoop,
    parent: ExecutionNode?,
    current: ExecutionNode? = null,
    overrideCurrent: Boolean = false,
    cause: Array<out ProcessTreeActivity> = parent?.lastExecuted.ifNullOrEmpty { parent?.cause.orEmpty() }
) : ExecutionNode(base, parent, cause) {

    private var doPhase = true

    private val redoPhase: Boolean
        get() = !doPhase

    private var current: ExecutionNode? = if (overrideCurrent) current else base.children[0].executionNode(this)

    override val available
        get() = if (!isComplete) {
            if (current != null)
                current!!.available
            else {
                assert(redoPhase)
                base.possibleOutcomes.asSequence().flatMap { it.node.executionNode(this).available }
            }
        } else emptySequence()

    override var isComplete: Boolean = false
        private set

    override var lastExecuted: Array<ProcessTreeActivity> = emptyArray()
        private set

    override fun postExecution(child: ExecutionNode) {
        require(child.parent === this)
        require(current === null || child === current)
        if (child.base === base.endLoopActivity) {
            check(redoPhase)
            isComplete = true
        } else {
            lastExecuted = child.lastExecuted
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

    override fun copy(): ProcessModelState =
        RedoLoopExecution(base, parent, this.current?.copy() as ExecutionNode?, true, cause).also {
            it.doPhase = this.doPhase
            it.isComplete = this.isComplete
            it.lastExecuted = this.lastExecuted
            it.current?.parent = it
        }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as RedoLoopExecution

        if (base != other.base) return false
        if (doPhase != other.doPhase) return false
        if (isComplete != other.isComplete) return false
        if (current != other.current) return false

        return true
    }

    override fun hashCode(): Int {
        var result = base.hashCode()
        result = 31 * result + doPhase.hashCode()
        result = 31 * result + isComplete.hashCode()
        result = 31 * result + (current?.hashCode() ?: 0)

        return result
    }
}
