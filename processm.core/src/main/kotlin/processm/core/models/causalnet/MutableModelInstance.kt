package processm.core.models.causalnet

import processm.core.models.commons.AbstractState
import processm.core.models.metadata.BasicStatistics
import processm.core.models.metadata.DefaultMetadataProvider
import processm.core.models.metadata.IntMetadata
import processm.core.models.metadata.MutableMetadataHandler

/**
 * A mutable model instance equipped with metadata providers corresponding to basic statistics
 */
class MutableModelInstance(model: Model, metadataHandler: MutableMetadataHandler) :
    ModelInstance(model, metadataHandler),
    MutableMetadataHandler by metadataHandler {

    init {
        for (name in BasicStatistics.BASIC_TIME_STATISTICS)
            addMetadataProvider(DefaultMetadataProvider<IntMetadata>(name))
    }

    internal var state = CausalNetState()

    override val currentState: AbstractState
        get() = state

    override val availableActivities
        get() = availableActivityExecutions.map { it.activity }

    override fun resetExecution() {
        state.clear()
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
        if (join != null) {
            check(state.containsAll(join.dependencies)) { "It is impossible to execute this join in the current state" }
            for (d in join.dependencies)
                state.remove(d)
        }
        if (split != null)
            state.addAll(split.dependencies)
    }

    override val availableActivityExecutions
        get() = sequence {
            if (state.isNotEmpty()) {
                for (node in state.map { it.target }.toSet())
                    for (join in model.joins[node].orEmpty())
                        if (state.containsAll(join.dependencies)) {
                            val splits = if (node != model.end) model.splits[node].orEmpty() else setOf(null)
                            yieldAll(splits.map { split ->
                                NodeExecution(node, this@MutableModelInstance, join, split)
                            })
                        }

            } else
                yieldAll(
                    model.splits.getValue(model.start)
                        .map { split -> NodeExecution(model.start, this@MutableModelInstance, null, split) })
        }
}