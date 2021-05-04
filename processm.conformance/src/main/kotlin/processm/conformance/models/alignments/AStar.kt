package processm.conformance.models.alignments

import processm.conformance.models.DeviationType
import processm.conformance.models.alignments.cache.Cache
import processm.core.helpers.ternarylogic.Ternary
import processm.core.log.Event
import processm.core.log.hierarchical.Trace
import processm.core.models.commons.Activity
import processm.core.models.commons.ProcessModel
import processm.core.models.commons.ProcessModelInstance
import processm.core.models.commons.ProcessModelState
import java.util.*


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
    override val model: ProcessModel,
    override val penalty: PenaltyFunction = PenaltyFunction()
) : Aligner {
    companion object {
        private const val SKIP_EVENT = Int.MIN_VALUE
    }

    private val activities: Set<String?> =
        model.activities.mapNotNullTo(HashSet()) { if (it.isSilent) null else it.name }

    private val endActivities: Set<String?> =
        model.endActivities.mapNotNullTo(HashSet()) { if (it.isSilent) null else it.name }

    /**
     * Calculates [Alignment] for the given [trace]. Use [Thread.interrupt] to cancel calculation without yielding result.
     *
     * @throws IllegalStateException If the alignment cannot be calculated, e.g., because the final model state is not
     * reachable.
     * @throws InterruptedException If the calculation cancelled.
     */
    override fun align(trace: Trace): Alignment {
        val events = trace.events.toList()
        val eventsWithExistingActivities = events.filter { e -> activities.contains(e.conceptName) }
        val alignment = alignInternal(eventsWithExistingActivities)
        if (events.size == eventsWithExistingActivities.size)
            return alignment
        return alignment.fillMissingEvents(events, penalty)
    }

    private fun alignInternal(events: List<Event>): Alignment {
        val thread = Thread.currentThread()
        val queue = PriorityQueue<SearchState>()
        val visited = Cache<SearchState>()

        val instance = model.createInstance()
        val initialProcessState = instance.currentState
        val initialSearchState = SearchState(
            currentCost = 0,
            predictedCost = predict(
                events,
                0,
                when {
                    instance.isFinalState -> Ternary.True
                    instance.availableActivities.any(Activity::isSilent) -> Ternary.Unknown
                    else -> Ternary.False
                }
            ),
            activity = null, // before first activity
            event = -1, // before first event
            previousSearchState = null,
            processState = initialProcessState
        )
        queue.add(initialSearchState)

        var upperBoundCost = Int.MAX_VALUE
        var lastCost = 0
        while (queue.isNotEmpty()) {
            val searchState = queue.poll()!!


            val newCost = searchState.currentCost + searchState.predictedCost
            assert(lastCost <= newCost)
            assert(newCost <= upperBoundCost)
            lastCost = newCost

            val prevProcessState = searchState.getProcessState(instance)
            instance.setState(prevProcessState)

            assert(with(searchState) { activity !== null || event != SKIP_EVENT || previousSearchState == null })

            val previousEventIndex = searchState.getPreviousEventIndex()
            val isFinalState = instance.isFinalState
            if (isFinalState) {
                if (previousEventIndex == events.size - 1) {
                    // we found the path
                    assert(searchState.predictedCost == 0) { "Predicted cost: ${searchState.predictedCost}." }
                    return formatAlignment(searchState, events)
                }

                // we found an upper bound
                val newUpperBoundCost =
                    searchState.currentCost + (events.size - previousEventIndex - 1) * penalty.logMove
                if (newUpperBoundCost < upperBoundCost) {
                    upperBoundCost = newUpperBoundCost
                    // Clearing the queue at this point is linear in the queue size and brings insignificant reductions
                    // that amount from 0 to few tens of search states in our experiments (compared to thousands or even
                    // millions of states in the queue). On the other hand, the states with the cost over the upperbound
                    // remaining in the queue will be never pushed to the root and/or reached, so keeping them is cheap.
                    // Note that we prevent below the insertion of new states of larger cost than the upperbound.
                    // Note also that the queue clean-up procedure can be done in O(log(n)) by replacing heap-based queue
                    // with the queue with total order. However, this may increase the time of other operations on the
                    // queue, and so we do go this direction.
                    // val sizeBefore = queue.size
                    // queue.removeIf { s -> s.currentCost + s.predictedCost > upperBoundCost }
                    // val fried = sizeBefore - queue.size
                    // if (fried > 0)
                    //    println("Found new upper bound cost of $upperBoundCost; removed $fried states from the queue; ${queue.size} states remain in the queue.")
                }
            }

            if (!visited.add(searchState))
                continue

            if (thread.isInterrupted) {
                throw InterruptedException("A* was requested to cancel.")
            }

            val nextEventIndex = when {
                previousEventIndex < events.size - 1 -> previousEventIndex + 1
                else -> SKIP_EVENT
            }
            val nextEvent = if (nextEventIndex != SKIP_EVENT) events[nextEventIndex] else null

            // add possible moves to the queue
            assert(instance.currentState === prevProcessState)
            for (activity in instance.availableActivities) {
                // silent activities are special
                if (activity.isSilent) {
                    if (activity.isArtificial) {
                        // just move the state of the model without moving in the log
                        instance.setState(prevProcessState.copy())
                        instance.getExecutionFor(activity).execute()
                        val state = searchState.copy(processState = instance.currentState)
                        if (!visited.contains(state)) {
                            queue.add(state)
                        }
                    } else {
                        val currentCost = searchState.currentCost + penalty.silentMove
                        // Pass Ternary.Unknown because obtaining the actual state requires execution in the model
                        val predictedCost = predict(events, nextEventIndex, Ternary.Unknown)
                            .coerceAtLeast(searchState.predictedCost - penalty.silentMove)
                        if (currentCost + predictedCost <= upperBoundCost) {
                            queue.add(
                                SearchState(
                                    currentCost = currentCost,
                                    predictedCost = predictedCost,
                                    activity = activity,
                                    event = SKIP_EVENT,
                                    previousSearchState = searchState
                                )
                            )
                        }
                    }
                    continue
                }

                assert(!activity.isSilent)

                // add synchronous move if applies
                if (isSynchronousMove(nextEvent, activity)) {
                    val currentCost = searchState.currentCost + penalty.synchronousMove
                    // Pass Ternary.Unknown because obtaining the actual state requires execution in the model
                    val predictedCost = predict(events, nextEventIndex + 1, Ternary.Unknown)
                        .coerceAtLeast(searchState.predictedCost - penalty.synchronousMove)
                    if (currentCost + predictedCost <= upperBoundCost) {
                        queue.add(
                            SearchState(
                                currentCost = currentCost,
                                predictedCost = predictedCost,
                                activity = activity,
                                event = nextEventIndex,
                                previousSearchState = searchState
                            )
                        )
                    }
                }

                // add model-only move
                run {
                    val currentCost = searchState.currentCost + penalty.modelMove
                    // Pass Ternary.Unknown because obtaining the actual state requires execution in the model
                    val predictedCost = predict(events, nextEventIndex, Ternary.Unknown)
                        .coerceAtLeast(searchState.predictedCost - penalty.modelMove)
                    if (currentCost + predictedCost <= upperBoundCost) {
                        queue.add(
                            SearchState(
                                currentCost = currentCost,
                                predictedCost = predictedCost,
                                activity = activity,
                                event = SKIP_EVENT,
                                previousSearchState = searchState
                            )
                        )
                    }
                }
            }

            // add log-only move
            if (nextEvent !== null) {
                val currentCost = searchState.currentCost + penalty.logMove
                val predictedCost = predict(
                    events, nextEventIndex + 1, when {
                        isFinalState -> Ternary.True
                        instance.availableActivities.any(Activity::isSilent) -> Ternary.Unknown
                        else -> Ternary.False
                    }
                ).coerceAtLeast(searchState.predictedCost - penalty.logMove)
                if (currentCost + predictedCost <= upperBoundCost) {
                    val state = SearchState(
                        currentCost = currentCost,
                        predictedCost = predictedCost,
                        activity = null,
                        event = nextEventIndex,
                        previousSearchState = searchState,
                        processState = prevProcessState
                    )
                    if (!visited.contains(state)) {
                        queue.add(state)
                    }
                }
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
                        modelState = processState,
                        logMove = if (event != SKIP_EVENT) events[event] else null,
                        logState = events.subList(0, getPreviousEventIndex() + 1).asSequence(),
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
        event !== null && event.conceptName == activity.name

    private fun predict(events: List<Event>, startIndex: Int, isFinalState: Ternary): Int {
        if (startIndex == SKIP_EVENT || startIndex >= events.size)
            return if (isFinalState != Ternary.False) 0 else penalty.modelMove // we reached the end of trace, should we move in the model?

        assert(startIndex in events.indices)

        var sum = 0
        if (isFinalState == Ternary.False && endActivities.isNotEmpty()) {
            var hasEndActivity = false
            for (index in startIndex until events.size) {
                if (endActivities.contains(events[index].conceptName)) {
                    hasEndActivity = true
                    break
                }
            }
            if (!hasEndActivity)
                sum += penalty.modelMove
        }

        return sum
    }

    private data class SearchState(
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
        val previousSearchState: SearchState?,
        var processState: ProcessModelState? = null
    ) : Comparable<SearchState> {
        fun getProcessState(instance: ProcessModelInstance): ProcessModelState {
            if (processState === null && activity !== null) {
                instance.setState(previousSearchState!!.getProcessState(instance).copy())
                instance.getExecutionFor(activity).execute()
                processState = instance.currentState
            }
            return processState ?: previousSearchState!!.getProcessState(instance)
        }

        fun getPreviousEventIndex(): Int {
            if (event != SKIP_EVENT)
                return event
            if (previousSearchState === null)
                return -1
            return previousSearchState.getPreviousEventIndex()
        }

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

            other as SearchState

            if (currentCost != other.currentCost) return false
            if (getPreviousEventIndex() != other.getPreviousEventIndex()) return false
            if (processState != other.processState) return false

            return true
        }

        override fun hashCode(): Int {
            var result = currentCost
            result = 31 * result + getPreviousEventIndex()
            result = 31 * result + processState.hashCode()
            return result
        }
    }
}
