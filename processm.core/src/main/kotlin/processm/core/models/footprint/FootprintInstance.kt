package processm.core.models.footprint

import processm.core.models.commons.ProcessModelInstance
import processm.core.models.commons.ProcessModelState

/**
 * Represents and instance of the [Footprint] matrix.
 */
class FootprintInstance(override val model: Footprint) : ProcessModelInstance {
    companion object {
        private val emptyState = object : ProcessModelState {
            override fun copy(): ProcessModelState = this
        }
    }

    override var currentState: ProcessModelState = emptyState

    override val availableActivities: Sequence<FootprintActivity>
        get() =
            if (currentState === emptyState) model.startActivities
            else model.matrix.getRow(currentState as FootprintActivity)
                .filterValues { it == Order.Parallel || it == Order.FollowedBy }.keys.asSequence()

    override val availableActivityExecutions: Sequence<FootprintActivityExecution>
        get() = availableActivities.map { FootprintActivityExecution(it, this) }

    override fun setState(state: ProcessModelState?) {
        this.currentState = state ?: emptyState
    }
}
