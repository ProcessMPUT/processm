package processm.conformance.models.antialignments

import processm.core.models.commons.*

internal class ReplayModel(var trace: List<Activity>) : ProcessModel {
    override val activities: Sequence<Activity>
        get() = trace.distinct().asSequence()
    override val startActivities: Sequence<Activity>
        get() = sequenceOf(trace.first())
    override val endActivities: Sequence<Activity>
        get() = sequenceOf(trace.last())
    override val decisionPoints: Sequence<DecisionPoint>
        get() = emptySequence()
    override val controlStructures: Sequence<ControlStructure>
        get() = emptySequence()

    override fun createInstance(): ProcessModelInstance = ReplayModelInstance(this)
}

internal class ReplayModelInstance(override val model: ReplayModel) : ProcessModelInstance, ActivityExecution {
    private var state: ReplayModelState = ReplayModelState(0)
    override val currentState: ProcessModelState
        get() = state
    override val availableActivities: Sequence<Activity>
        get() =
            if (state.index >= model.trace.size) emptySequence()
            else sequenceOf(model.trace[state.index])
    override val availableActivityExecutions: Sequence<ActivityExecution>
        get() =
            if (state.index >= model.trace.size) emptySequence()
            else sequenceOf(this)

    override val isFinalState: Boolean
        get() = state.index >= model.trace.size

    override fun setState(state: ProcessModelState?) {
        if (state === null)
            this.state = ReplayModelState(0)
        this.state = state!! as ReplayModelState
    }

    override val activity: Activity
        get() = model.trace[state.index]

    override fun execute() {
        state.index = state.index + 1
    }

    override fun getExecutionFor(activity: Activity): ActivityExecution {
        assert(activity == model.trace[state.index])
        return this
    }
}

/**
 * @property index The index of the next activity to execute.
 */
internal data class ReplayModelState(var index: Int) : ProcessModelState {
    override fun copy(): ProcessModelState = ReplayModelState(index)
}
