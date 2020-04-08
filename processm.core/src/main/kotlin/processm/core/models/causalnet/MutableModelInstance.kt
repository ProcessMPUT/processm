package processm.core.models.causalnet

import processm.core.models.commons.AbstractState
import processm.core.models.metadata.BasicStatistics
import processm.core.models.metadata.DefaultMetadataProvider
import processm.core.models.metadata.IntMetadata
import processm.core.models.metadata.MutableMetadataHandler

/**
 * A mutable model instance equipped with metadata providers corresponding to basic statistics
 */
class MutableModelInstance(
    model: Model,
    metadataHandler: MutableMetadataHandler,
    val initialState: CausalNetState = CausalNetState()
) :
    ModelInstance(model, metadataHandler),
    MutableMetadataHandler by metadataHandler {

    init {
        for (name in BasicStatistics.BASIC_TIME_STATISTICS)
            addMetadataProvider(DefaultMetadataProvider<IntMetadata>(name))
    }

    internal var state: CausalNetState = CausalNetState()

    init {
        resetExecution()
    }

    override val currentState: AbstractState
        get() = state

    override val availableActivities
        get() = availableActivityExecutions.map { it.activity }

    override fun resetExecution() {
        state.clear()
        state.addAll(initialState)
    }

    internal fun execute(join: Join?, split: Split?) {
        require(join != null || split != null) { "At least one of the arguments must be non-null" }
        if (join != null) {
            require(model.joins[join.target]?.contains(join) == true) { "Cannot execute a join not present in the model" }
            if (split != null)
                require(join.target == split.source) { "Join and split must concern the same node" }
            else
                require(model.outgoing[join.target].isNullOrEmpty()) { "Can skip split only for the end node" }
        }
        if (split != null) {
            require(model.splits[split.source]?.contains(split) == true) { "Cannot execute a split not present in the model" }
            if (join == null)
                require(model.incoming[split.source].isNullOrEmpty()) { "Can skip start only for the start node" }
        }
        state.execute(join, split)
    }

    override val availableActivityExecutions
        get() = model.available(state).map { NodeExecution(it.activity, this, it.join, it.split) }
}