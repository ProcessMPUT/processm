package processm.conformance.models.footprint

import processm.conformance.models.alignments.cache.Cache
import processm.core.log.XESInputStream
import processm.core.models.commons.Activity
import processm.core.models.commons.ProcessModel
import processm.core.models.commons.ProcessModelState
import processm.core.models.footprint.*
import processm.helpers.map2d.DoublingMap2D

/**
 * A comparator for [Footprint]s of [model] and a log that builds the [modelFootprint] using depth-first-search in
 * the graph of model states.
 *
 * @property model The process model.
 * @property maxSubgraphDepth The limit on the length of the search path from the last search state a new
 * directly-follows relation was observed in. The search time depends exponentially on this parameter for large models
 * and linearly for small models. Increase for more complete results, decrease for better performance.
 * @property subgraphBudget The limit on the total number of search states visited in a subgraph of the last search
 * state a new directly-follows relation was observed in. Every found directly-follows relation resets the budget
 * for the subgraph starting in the current search state. The search time depends linearly on this parameter.
 * Increase for more complete results, decrease for better
 * performance.
 */
class DepthFirstSearch(
    val model: ProcessModel,
    val maxSubgraphDepth: Int = 10,
    val subgraphBudget: Int = 1000
) {
    init {
        require(maxSubgraphDepth >= 1) { "Max depth steps without new relation must be at least 1." }
        require(subgraphBudget >= 1) { "Subgraph budget must be at least 1." }
    }


    private val modelFootprint: Footprint = if (model is Footprint) model else dfs()

    private fun dfs(): Footprint {
        val matrix = DoublingMap2D<FootprintActivity, FootprintActivity, Order>()
        val instance = model.createInstance()
        val stack = ArrayDeque<SearchState>()
        val visited = Cache<SearchState>()

        stack.add(
            SearchState(
                0,
                subgraphBudget,
                null,
                instance.availableActivities.iterator(),
                instance.currentState.copy()
            )
        )

        while (stack.isNotEmpty()) {
            val state = stack.last()

            instance.setState(state.processState)

            // Do not terminate when instance.isFinalState==true, since some models, e.g., Petri nets having a transition
            // without preceding place may still contain enabled activities. The execution of these activities is crucial
            // to observe directly-follows relation between these activities and the "final" activity.
            // See test DepthFirstSearchTests.`PM book Fig 7 3 conforming log`().
            if (!state.nextActivities.hasNext() || stack.size - maxSubgraphDepth > state.newRelationDepth || state.budget < 0) {
                stack.removeLast()
                val last = stack.lastOrNull()
                if (last !== null) {
                    if (last.newRelationDepth < stack.size) {
                        last.budget = state.budget
                    }
                }
                continue
            }

            val originalActivity = state.nextActivities.next()
            val activity = originalActivity.toFootprintActivity()

            if (state.lastActivity !== null && !originalActivity.isSilent) {
                val last = matrix[state.lastActivity, activity]
                if (last != Order.Parallel) {
                    // store the observed directly-follows relation
                    if (state.lastActivity == activity) {
                        assert(last != Order.Parallel)

                        matrix[state.lastActivity, activity] = Order.Parallel
                        state.newRelationDepth = stack.size
                        // println("Found new relation footprint[${state.lastActivity}, $activity]=∥")
                    } else {
                        val new = when (last) {
                            null, Order.NoOrder -> Order.FollowedBy
                            Order.PrecededBy -> Order.Parallel
                            else -> last
                        }
                        if (last != new) {
                            matrix[state.lastActivity, activity] = new
                            matrix[activity, state.lastActivity] = new.invert()
                            state.newRelationDepth = stack.size
                            state.budget = subgraphBudget
                            // println("Found new relation footprint[${state.lastActivity}, $activity]=$new")
                        }
                    }
                }
            }

            if (stack.size - maxSubgraphDepth >= state.newRelationDepth || state.budget <= 0 || instance.isFinalState)
                continue

            instance.setState(state.processState.copy())
            val execution = instance.getExecutionFor(originalActivity)
            execution.execute()

            val nextState = SearchState(
                newRelationDepth = state.newRelationDepth,
                budget = state.budget - 1,
                lastActivity = if (originalActivity.isSilent) state.lastActivity else activity,
                nextActivities = instance.availableActivities.iterator(),
                processState = instance.currentState
            )

            if (!visited.add(nextState))
                continue

            stack.addLast(nextState)
        }

        matrix.fillNulls()
        return Footprint(matrix)
    }

    private fun Activity.toFootprintActivity(): FootprintActivity {
        if (this is FootprintActivity)
            return this
        return FootprintActivity(this.name)
    }

    private class SearchState(
        var newRelationDepth: Int,
        var budget: Int,
        /**
         * Only labelled activities.
         */
        val lastActivity: FootprintActivity? = null,
        val nextActivities: Iterator<Activity>,
        val processState: ProcessModelState
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is SearchState) return false

            if (lastActivity != other.lastActivity) return false
            if (processState != other.processState) return false

            return true
        }

        override fun hashCode(): Int {
            var result = lastActivity?.hashCode() ?: 0
            result = 31 * result + processState.hashCode()
            return result
        }
    }

    /**
     * Calculates [DifferentialFootprint] for [model] and the given [log].
     */
    fun assess(log: XESInputStream): DifferentialFootprint {
        val logFootprint = log.toFootprint()
        val activities = modelFootprint.activities.toHashSet()
        activities.addAll(logFootprint.activities)
        return DifferentialFootprint(logFootprint, modelFootprint)
    }
}
