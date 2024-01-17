package processm.core.models.processtree

import processm.core.models.commons.ProcessModelInstance
import processm.core.models.commons.ProcessModelState
import processm.core.models.processtree.execution.ActivityExecution
import processm.core.models.processtree.execution.ExecutionNode

class ProcessTreeInstance(override val model: ProcessTree) : ProcessModelInstance {
    private val root = model.root ?: throw IllegalArgumentException("Cannot execute an empty model")

    override lateinit var currentState: ExecutionNode
        private set

    init {
        setState(null)
    }

    override val availableActivities
        get() = currentState.available.map(ActivityExecution::base)

    override val availableActivityExecutions
        get() = currentState.available

    override fun setState(state: ProcessModelState?) {
        currentState =
            if (state === null) {
                root.executionNode(null)
            } else {
                require(state is ExecutionNode) { "The given object is not a valid Process Tree state." }
                state
            }
    }
}
