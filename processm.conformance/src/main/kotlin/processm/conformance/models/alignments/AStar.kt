package processm.conformance.models.alignments

import processm.conformance.models.DeviationType
import processm.core.log.Event
import processm.core.log.hierarchical.Trace
import processm.core.models.commons.Activity
import processm.core.models.commons.ProcessModel
import processm.core.models.commons.ProcessModelState
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
) {
    companion object {
        private const val SKIP_EVENT = Int.MIN_VALUE
    }

    private val activities: Set<String> by lazy(LazyThreadSafetyMode.PUBLICATION) {
        model.activities.mapNotNullTo(HashSet()) { if (it.isSilent) null else it.name }
    }

    private val endActivities: Set<String> by lazy(LazyThreadSafetyMode.PUBLICATION) {
        model.endActivities.mapNotNullTo(HashSet()) { if (it.isSilent) null else it.name }
    }

    fun align(trace: Trace): Alignment {
        val events = trace.events.toList()

        val queue = PriorityQueue<SearchState>()

        val instance = model.createInstance()
        val initialProcessState = instance.currentState.copy()
        val initialSearchState = SearchState(
            processStateFactory = lazyOf(initialProcessState),
            currentCost = 0,
            predictedCost = predict(events, 0),
            activity = null, // before first activity
            event = -1, // before first event
            previousSearchState = null
        )
        queue.add(initialSearchState)

        while (queue.isNotEmpty()) {
            val searchState = queue.poll()!!
            instance.setState(searchState.processStateFactory.value)

            assert(with(searchState) { activity !== null || event != SKIP_EVENT || previousSearchState == null })

            val previousEventIndex = getPreviousEventIndex(searchState)
            if (previousEventIndex == events.size - 1 && instance.isFinalState) {
                // we found the path
                val steps = ArrayList<Step>()
                var state: SearchState = searchState
                while (state !== initialSearchState) {
                    with(state) {
                        steps.add(
                            Step(
                                modelMove = activity,
                                modelState = processStateFactory.value,
                                logMove = if (event != SKIP_EVENT) events[event] else null,
                                logState = trace.events.take(getPreviousEventIndex(this) + 1),
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

                assert(searchState.predictedCost == 0) { "Predicted cost: ${searchState.predictedCost}." }
                return Alignment(steps, searchState.currentCost)
            }

            val prevProcessState = searchState.processStateFactory.value
            val nextEventIndex = when {
                previousEventIndex < 0 -> 0
                previousEventIndex < events.size - 1 -> previousEventIndex + 1
                else -> SKIP_EVENT
            }
            val nextEvent = if (nextEventIndex != SKIP_EVENT) events[nextEventIndex] else null

            // add possible moves to the queue
            for ((execIndex, execution) in instance.availableActivityExecutions.withIndex()) {
                val factory = {
                    instance.setState(prevProcessState.copy())
                    instance.availableActivityExecutions.elementAt(execIndex).execute()
                    instance.currentState
                }

                // silent activities are special
                if (execution.activity.isSilent) {
                    if (execution.activity.isArtificial) {
                        // just move the state of the model without moving in the log
                        queue.add(
                            searchState.copy(processStateFactory = lazy(LazyThreadSafetyMode.NONE, factory))
                        )
                    } else {
                        queue.add(
                            SearchState(
                                processStateFactory = lazy(LazyThreadSafetyMode.NONE, factory),
                                currentCost = searchState.currentCost + penalty.silentMove,
                                predictedCost = predict(events, nextEventIndex),
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
                            processStateFactory = lazy(LazyThreadSafetyMode.NONE, factory),
                            currentCost = searchState.currentCost + penalty.synchronousMove,
                            predictedCost = predict(events, nextEventIndex + 1),
                            activity = execution.activity,
                            event = nextEventIndex,
                            previousSearchState = searchState
                        )
                    )

                // add model-only move
                queue.add(
                    SearchState(
                        processStateFactory = lazy(LazyThreadSafetyMode.NONE, factory),
                        currentCost = searchState.currentCost + penalty.modelMove,
                        predictedCost = predict(events, getPreviousEventIndex(searchState) + 1),
                        activity = execution.activity,
                        event = SKIP_EVENT,
                        previousSearchState = searchState
                    )
                )
            }

            // add log-only move
            // skip if all available activities in model are silent, as move in the log is pointless in this case
            if (nextEvent !== null && !instance.availableActivities.all { it.isSilent })
                queue.add(
                    SearchState(
                        processStateFactory = lazyOf(prevProcessState),
                        currentCost = searchState.currentCost + penalty.logMove,
                        predictedCost = predict(events, nextEventIndex + 1),
                        activity = null,
                        event = nextEventIndex,
                        previousSearchState = searchState
                    )
                )
        }

        assert(false) { "Cannot find the alignment. This should not happen ever since A* is guaranteed to find a path in the state graph." }
        throw IllegalStateException("Cannot align the log with the model.")
    }

    private fun isSynchronousMove(event: Event?, activity: Activity): Boolean =
        event !== null && !activity.isSilent && event.conceptName == activity.name

    private fun predict(events: List<Event>, startIndex: Int): Int {
        if (startIndex == SKIP_EVENT || startIndex >= events.size)
            return 0 // we reached the end of trace

        assert(startIndex in events.indices)
        var sum =
            if (
                endActivities.isNotEmpty() &&
                events.subList(startIndex, events.size).none { it.conceptName in endActivities }
            )
                penalty.modelMove
            else penalty.synchronousMove

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
            val otherTotalCost = with(other) { currentCost + predictedCost }

            return if (myTotalCost == otherTotalCost) predictedCost.compareTo(other.predictedCost) // DFS-based second-order queuing criterion
            else myTotalCost.compareTo(otherTotalCost)
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
