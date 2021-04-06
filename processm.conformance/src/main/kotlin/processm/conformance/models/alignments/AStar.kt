package processm.conformance.models.alignments

import processm.conformance.models.DeviationType
import processm.core.helpers.map2d.DoublingMap2D
import processm.core.helpers.ternarylogic.Ternary
import processm.core.helpers.ternarylogic.Ternary.Companion.toTernary
import processm.core.log.Event
import processm.core.log.hierarchical.Trace
import processm.core.models.commons.Activity
import processm.core.models.commons.ProcessModel
import processm.core.models.commons.ProcessModelState
import java.lang.Integer.min
import java.util.*
import kotlin.collections.ArrayList
import kotlin.collections.HashSet


/**
 * An A*-based calculator of alignments. The implementation is based on the priority queue, the naive heuristics, and
 * DFS-based second-order queuing criterion as described in Sebastiaan J. van Zelst, Alfredo Bolt, and
 * Boudewijn F. van Dongen, Tuning Alignment Computation: An Experimental Evaluation.
 * It is model-representation agnostic. It works for all model representations and does not convert them to Petri net
 * (like in the above-mentioned paper).
 *
 * This class is thread-safe.
 */
class AStar(
    val model: ProcessModel,
    val penalty: PenaltyFunction = PenaltyFunction()
) : Aligner {
    companion object {
        private const val SKIP_EVENT = Int.MIN_VALUE
        private const val VISITED_CACHE_LIMIT = 10000
        private const val VISITED_CACHE_FREE = 50
    }

    private val activities: Set<String> by lazy(LazyThreadSafetyMode.PUBLICATION) {
        model.activities.mapNotNullTo(HashSet()) { if (it.isSilent) null else it.name }
    }

    private val endActivities: Set<String> by lazy(LazyThreadSafetyMode.PUBLICATION) {
        model.endActivities.mapNotNullTo(HashSet()) { if (it.isSilent) null else it.name }
    }

    override fun align(trace: Trace): Alignment {
        val events = trace.events.toList()

        val queue = PriorityQueue<SearchState>()
        val visited = DoublingMap2D<Int, Int, HashSet<ProcessModelState>>()

        val instance = model.createInstance()
        val initialProcessState = instance.currentState
        val initialSearchState = SearchState(
            processStateFactory = lazyOf(initialProcessState),
            currentCost = 0,
            predictedCost = predict(
                events,
                0,
                instance.availableActivityExecutions.none { !it.activity.isSilent }.toTernary()
            ),
            activity = null, // before first activity
            event = -1, // before first event
            previousSearchState = null
        )
        queue.add(initialSearchState)

        var lastCost = 0
        while (queue.isNotEmpty()) {
            val searchState = queue.poll()!!

            assert(lastCost <= searchState.currentCost + searchState.predictedCost)
            lastCost = searchState.currentCost + searchState.predictedCost

            val prevProcessState = searchState.processStateFactory.value
            instance.setState(prevProcessState)

            assert(with(searchState) { activity !== null || event != SKIP_EVENT || previousSearchState == null })

            val previousEventIndex = getPreviousEventIndex(searchState)
            if (previousEventIndex == events.size - 1 && instance.isFinalState) {
                // we found the path
                assert(searchState.predictedCost == 0) { "Predicted cost: ${searchState.predictedCost}." }
                return formatAlignment(searchState, events)
            }

            val v = visited.compute(previousEventIndex, searchState.currentCost) { _, _, v ->
                v ?: HashSet(VISITED_CACHE_LIMIT + 1)
            }!!
            if (!v.add(prevProcessState)) {
                continue
            }

            if (v.size >= VISITED_CACHE_LIMIT) {
                // dropping more states than 1 turns out crucial to run fast
                val it = v.iterator()
                for (i in 0 until min(VISITED_CACHE_FREE, v.size)) {
                    it.next()
                    it.remove()
                }
            }
            assert(v.size < VISITED_CACHE_LIMIT)

//            if (visitedTheSameState(searchState))
//                continue

            val nextEventIndex = when {
                previousEventIndex < events.size - 1 -> previousEventIndex + 1
                else -> SKIP_EVENT
            }
            val nextEvent = if (nextEventIndex != SKIP_EVENT) events[nextEventIndex] else null

            if (nextEvent === null || nextEvent.conceptName in activities) {
                // add possible moves to the queue
                assert(instance.currentState === prevProcessState)
                for ((execIndex, execution) in instance.availableActivityExecutions.withIndex()) {
                    fun factory(): ProcessModelState {
                        instance.setState(prevProcessState.copy())
                        instance.availableActivityExecutionAt(execIndex).execute()
                        return instance.currentState
                    }

                    // silent activities are special
                    if (execution.activity.isSilent) {
                        if (execution.activity.isArtificial) {
                            // just move the state of the model without moving in the log
                            queue.add(
                                searchState.copy(processStateFactory = lazy(LazyThreadSafetyMode.NONE, ::factory))
                            )
                        } else {
                            queue.add(
                                SearchState(
                                    processStateFactory = lazy(LazyThreadSafetyMode.NONE, ::factory),
                                    currentCost = searchState.currentCost + penalty.silentMove,
                                    // Pass Ternary.Unknown because obtaining the actual state requires execution in the model
                                    predictedCost = predict(events, nextEventIndex, Ternary.Unknown)
                                        .coerceAtLeast(searchState.predictedCost - penalty.silentMove),
                                    activity = execution.activity,
                                    event = SKIP_EVENT,
                                    previousSearchState = searchState
                                )
                            )
                        }
                        continue
                    }

                    // add synchronous move if applies
                    if (isSynchronousMove(nextEvent, execution.activity))
                        queue.add(
                            SearchState(
                                processStateFactory = lazy(LazyThreadSafetyMode.NONE, ::factory),
                                currentCost = searchState.currentCost + penalty.synchronousMove,
                                // Pass Ternary.Unknown because obtaining the actual state requires execution in the model
                                predictedCost = predict(events, nextEventIndex + 1, Ternary.Unknown)
                                    .coerceAtLeast(searchState.predictedCost - penalty.synchronousMove),
                                activity = execution.activity,
                                event = nextEventIndex,
                                previousSearchState = searchState
                            )
                        )

                    // add model-only move
                    queue.add(
                        SearchState(
                            processStateFactory = lazy(LazyThreadSafetyMode.NONE, ::factory),
                            currentCost = searchState.currentCost + penalty.modelMove,
                            // Pass Ternary.Unknown because obtaining the actual state requires execution in the model
                            predictedCost = predict(events, nextEventIndex, Ternary.Unknown)
                                .coerceAtLeast(searchState.predictedCost - penalty.modelMove),
                            activity = execution.activity,
                            event = SKIP_EVENT,
                            previousSearchState = searchState
                        )
                    )
                }
            }

            // add log-only move
            if (nextEvent !== null) {
                val predictedCost = predict(events, nextEventIndex + 1, when {
                    instance.isFinalState -> Ternary.True
                    instance.availableActivityExecutions.any { it.activity.isSilent } -> Ternary.Unknown
                    else -> Ternary.False
                }).coerceAtLeast(searchState.predictedCost - penalty.logMove)
                queue.add(
                    SearchState(
                        processStateFactory = lazyOf(prevProcessState),
                        currentCost = searchState.currentCost + penalty.logMove,
                        predictedCost = predictedCost,
                        activity = null,
                        event = nextEventIndex,
                        previousSearchState = searchState
                    )
                )
            }
        }

        throw IllegalStateException("Cannot align the log with the model. The final state of the model is not reachable.")
    }

    private fun formatAlignment(
        searchState: SearchState,
        events: List<Event>
    ): Alignment {
        val steps = ArrayList<Step>()
        var state: SearchState = searchState
        while (state.event != -1) {
            with(state) {
                steps.add(
                    Step(
                        modelMove = activity,
                        modelState = processStateFactory.value,
                        logMove = if (event != SKIP_EVENT) events[event] else null,
                        logState = events.subList(0, getPreviousEventIndex(this) + 1).asSequence(),
                        type = when {
                            activity === null -> DeviationType.LogDeviation
                            event == SKIP_EVENT -> DeviationType.ModelDeviation
                            else -> DeviationType.None
                        }
                    )
                )
            }

            state = state.previousSearchState!!
        }
        steps.reverse()

        return Alignment(steps, searchState.currentCost)
    }

    private fun isSynchronousMove(event: Event?, activity: Activity): Boolean =
        event !== null && !activity.isSilent && event.conceptName == activity.name

    private fun predict(events: List<Event>, startIndex: Int, isFinalState: Ternary): Int {
        if (startIndex == SKIP_EVENT || startIndex >= events.size)
            return if (isFinalState != Ternary.False) 0 else penalty.modelMove // we reached the end of trace, should we move in the model?

        assert(startIndex in events.indices)

        var sum = 0
        if (isFinalState == Ternary.False && endActivities.isNotEmpty()) {
            var hasEndActivity = false
            for (index in startIndex until events.size) {
                if (events[index].conceptName in endActivities) {
                    hasEndActivity = true
                    break
                }
            }
            if (!hasEndActivity)
                sum += penalty.modelMove
        }

        for (index in startIndex until events.size) {
            sum +=
                if (events[index].conceptName in activities) penalty.synchronousMove
                else penalty.logMove
        }

        return sum
    }

    private tailrec fun getPreviousEventIndex(state: SearchState): Int {
        if (state.event != SKIP_EVENT)
            return state.event
        if (state.previousSearchState === null)
            return -1
        return getPreviousEventIndex(state.previousSearchState)
    }

    private fun visitedTheSameState(state: SearchState): Boolean =
        state.previousSearchState !== null &&
                visitedTheSameState(state.previousSearchState, state, getPreviousEventIndex(state))

    private tailrec fun visitedTheSameState(
        state: SearchState,
        finalState: SearchState,
        finalStateEvent: Int
    ): Boolean {
        if (state.currentCost < finalState.currentCost || getPreviousEventIndex(state) < finalStateEvent)
            return false
        assert(state.currentCost == finalState.currentCost)
        if (state.processStateFactory.value == finalState.processStateFactory.value)
            return true
        if (state.previousSearchState === null)
            return false
        return visitedTheSameState(state.previousSearchState, finalState, finalStateEvent)
    }

    private data class SearchState(
        val processStateFactory: Lazy<ProcessModelState>,
        /**
         * The cost up to this state.
         */
        val currentCost: Int,
        /**
         * The predicted cost of reaching the end state.
         * This value must be less than or equal the actual value.
         */
        val predictedCost: Int,
        /**
         * The last executed activity or null if no move in the model has been done.
         */
        val activity: Activity?,
        /**
         * The last executed event.
         */
        val event: Int,
        val previousSearchState: SearchState?
    ) : Comparable<SearchState> {
        override fun compareTo(other: SearchState): Int {
            val myTotalCost = currentCost + predictedCost
            val otherTotalCost = other.currentCost + other.predictedCost

            return when {
                myTotalCost != otherTotalCost -> myTotalCost.compareTo(otherTotalCost)
                predictedCost != other.predictedCost -> predictedCost.compareTo(other.predictedCost) // DFS-based second-order queuing criterion
                event >= 0 && other.event >= 0 && event != other.event -> other.event.compareTo(event) // prefer more complete traces
                event < 0 && other.event >= 0 -> 1 // prefer moves with events
                event >= 0 && other.event < 0 -> -1
                else -> (activity === null).compareTo(other.activity === null) // prefer moves with activities
            }
        }

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false

            other as SearchState

            if (currentCost != other.currentCost) return false
            if (predictedCost != other.predictedCost) return false
            if (activity != other.activity) return false
            if (event != other.event) return false
            if (processStateFactory != other.processStateFactory) return false
            if (previousSearchState != other.previousSearchState) return false

            return true
        }

        override fun hashCode(): Int {
            var result = currentCost
            result = 31 * result + predictedCost
            result = 31 * result + (activity?.hashCode() ?: 0)
            result = 31 * result + event
            return result
        }


    }
}
