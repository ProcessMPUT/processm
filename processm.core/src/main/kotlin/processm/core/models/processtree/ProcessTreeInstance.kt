package processm.core.models.processtree

import processm.core.models.commons.ProcessModelInstance
import processm.core.models.processtree.execution.ExecutionNode

class ProcessTreeInstance(override val model: ProcessTree) : ProcessModelInstance {
    private val root = model.root ?: throw IllegalArgumentException("Cannot execute an empty model")

    override lateinit var currentState: ExecutionNode
        private set

    init {
        resetExecution()
    }

    override val availableActivities
        get() = currentState.available.map { it.base }

    override val availableActivityExecutions
        get() = currentState.available

    override fun resetExecution() {
        currentState = root.executionNode(null)
    }
}