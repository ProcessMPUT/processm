package processm.core.models.causalnet

import processm.core.models.commons.Activity
import processm.core.models.commons.ActivityExecution
import processm.core.models.commons.ProcessModelState
import processm.core.models.metadata.MutableMetadataHandler

/**
 * A mutable model instance equipped with metadata providers corresponding to basic statistics
 */
class MutableCausalNetInstance(
    model: CausalNet,
    metadataHandler: MutableMetadataHandler
) :
    CausalNetInstance(model, metadataHandler),
    MutableMetadataHandler by metadataHandler {

    internal var state: CausalNetState = CausalNetState()

    init {
        setState(null)
    }

    override val currentState: ProcessModelState
        get() = state

    override val availableActivities
        get() = model.available(state)

    override val isFinalState: Boolean
        get() = !state.isFresh && state.isEmpty()

    override fun setState(state: ProcessModelState?) {
        if (state === null) {
            this.state.clear()
        } else {
            require(state is CausalNetState) { "The given object is not a valid Causal Net state." }
            this.state = state
        }
    }

    /**
     * Executes given [join] and [split] to change the current [state]
     *
     * @param join may be null only for the start node
     * @param split may be null only for the end node
     */
    internal fun execute(join: Join?, split: Split?) {
        require(join !== null || split !== null) { "At least one of the arguments must be non-null" }
        if (join !== null) {
            require(model.joins[join.target]?.contains(join) == true) { "Cannot execute a join not present in the model" }
            if (split !== null)
                require(join.target == split.source) { "Join and split must concern the same node" }
            else
                require(model.outgoing[join.target].isNullOrEmpty()) { "Can skip split only for the end node" }
        }
        if (split !== null) {
            require(model.splits[split.source]?.contains(split) == true) { "Cannot execute a split not present in the model" }
            if (join === null)
                require(model.incoming[split.source].isNullOrEmpty()) { "Can skip start only for the start node" }
        }
        state.execute(join, split)
    }

    override val availableActivityExecutions
        get() = model.available(state).map { NodeExecution(it.activity, this, it.join, it.split) }

    override fun getExecutionFor(activity: Activity): ActivityExecution {
        check(activity is DecoupledNodeExecution && model.isAvailable(activity, state))
        return with(activity) { NodeExecution(this.activity, this@MutableCausalNetInstance, join, split) }
    }
}
