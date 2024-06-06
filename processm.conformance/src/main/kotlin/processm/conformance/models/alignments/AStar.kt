package processm.conformance.models.alignments

import processm.conformance.models.DeviationType
import processm.conformance.models.alignments.cache.Cache
import processm.core.log.Event
import processm.core.log.hierarchical.Trace
import processm.core.models.causalnet.CausalNet
import processm.core.models.commons.Activity
import processm.core.models.commons.ProcessModel
import processm.core.models.commons.ProcessModelInstance
import processm.core.models.commons.ProcessModelState
import processm.core.models.petrinet.PetriNet
import processm.helpers.asCollection
import processm.helpers.ternarylogic.Ternary
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
    override val penalty: PenaltyFunction = PenaltyFunction(),
    // FIXME: #155 CountUnmatchedPetriNetMoves keeps trace-specific state and so concurrent calls to align() are not thread-safe contrary to the class documentation.
    private val countUnmatchedModelMoves: CountUnmatchedModelMoves? =
        when (model) {
            is PetriNet -> CountUnmatchedPetriNetMoves(model)
            is CausalNet -> CountUnmatchedCausalNetMoves(model)
            else -> null
        },
    private val countUnmatchedLogMoves: CountUnmatchedLogMoves? =
        when (model) {
            is CausalNet -> CountUnmatchedLogMovesInCausalNet(model)
            else -> null
        }
) : Aligner {
    companion object {
        private const val SKIP_EVENT = Short.MIN_VALUE
        private const val QUEUE_INITIAL_CAP = 1 shl 7 // the default is 11
    }

    private val activities: Set<String?> =
        model.activities.mapNotNullTo(HashSet()) { if (it.isSilent) null else it.name }

    private val endActivities: Set<String?> =
        model.endActivities.mapNotNullTo(HashSet()) { if (it.isSilent) null else it.name }

    /**
     * Statistics for tests only.
     */
    @Deprecated("This property is not thread safe and concurrent runs of align() may yield unpredictable results. Do not use, except in the single-threaded tests.")
    internal var visitedStatesCount: Int = 0
        private set

    /**
     * Calculates [Alignment] for the given [trace]. Use [Thread.interrupt] to cancel calculation without yielding result.
     *
     * @param trace The trace to align.
     * @param costUpperBound The maximal cost of the resulting alignment.
     * @return The alignment with the minimal cost or null if an alignment with the cost within the [costUpperBound] does
     * not exist.
     * @throws InterruptedException If the calculation cancelled.
     */
    override fun align(trace: Trace, costUpperBound: Int): Alignment? {
        val events = trace.events.asCollection()
        val eventsWithExistingActivities = events.filter { e -> activities.contains(e.conceptName) }
        visitedStatesCount = 0
        val alignment = alignInternal(eventsWithExistingActivities, costUpperBound)
        if (events.size == eventsWithExistingActivities.size)
            return alignment
        return alignment?.fillMissingEvents(events, penalty)?.also { if (it.cost > costUpperBound) return@align null }
    }

    private fun alignInternal(events: List<Event>, costLimit: Int): Alignment? {
        countUnmatchedLogMoves?.reset()
        val nEvents = countRemainingEventsWithTheSameConceptName(events)
        val thread = Thread.currentThread()
        val queue = PriorityQueue<SearchState>(QUEUE_INITIAL_CAP)
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
                },
                initialProcessState,
                nEvents,
                null
            ).toShort(),
            activity = null, // before first activity
            cause = emptyList(),
            event = -1, // before first event
            previousSearchState = null,
            processState = initialProcessState
        )
        queue.add(initialSearchState)

        var upperBoundCost = costLimit
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
                    assert(searchState.predictedCost == 0.toShort()) { "Predicted cost: ${searchState.predictedCost}." }
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
                    // remaining in the queue will never be pushed to the root and/or reached, so keeping them is cheap.
                    // Note that we prevent below the insertion of new states of larger cost than the upperbound.
                    // Note also that the queue clean-up procedure can be done in O(log(n)) by replacing heap-based queue
                    // with the queue with total order. However, this may increase the time of other operations on the
                    // queue, and so we do not go this direction.
                    // val sizeBefore = queue.size
                    // queue.removeIf { s -> s.currentCost + s.predictedCost > upperBoundCost }
                    // val fried = sizeBefore - queue.size
                    // if (fried > 0)
                    //    println("Found new upper bound cost of $upperBoundCost; removed $fried states from the queue; ${queue.size} states remain in the queue.")
                }
            }

            if (!visited.add(searchState))
                continue

            visitedStatesCount++

            if (thread.isInterrupted) {
                throw InterruptedException("A* was requested to cancel.")
            }

            val nextEventIndex = previousEventIndex + 1
            val nextEvent = if (nextEventIndex < events.size) events[nextEventIndex] else null

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
                        val predictedCost =
                            predict(events, nextEventIndex, Ternary.Unknown, prevProcessState, nEvents, activity)
                        //.coerceAtLeast(lastCost - currentCost)
                        assert(predictedCost >= lastCost - currentCost)
                        if (currentCost + predictedCost <= upperBoundCost) {
                            queue.add(
                                SearchState(
                                    currentCost = currentCost.toShort(),
                                    predictedCost = predictedCost.toShort(),
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
                    val predictedCost =
                        predict(
                            events,
                            nextEventIndex + 1,
                            Ternary.Unknown,
                            searchState.processState!!,
                            nEvents,
                            activity
                        )
                    //.coerceAtLeast(lastCost - currentCost)
                    assert(predictedCost >= lastCost - currentCost)
                    if (currentCost + predictedCost <= upperBoundCost) {
                        queue.add(
                            SearchState(
                                currentCost = currentCost.toShort(),
                                predictedCost = predictedCost.toShort(),
                                activity = activity,
                                event = nextEventIndex.toShort(),
                                previousSearchState = searchState
                            )
                        )
                    }
                }

                // add model-only move
                run {
                    val currentCost = searchState.currentCost + penalty.modelMove
                    // Pass Ternary.Unknown because obtaining the actual state requires execution in the model
                    val predictedCost =
                        predict(events, nextEventIndex, Ternary.Unknown, searchState.processState!!, nEvents, activity)
                    //.coerceAtLeast(lastCost - currentCost)
                    assert(predictedCost >= lastCost - currentCost)
                    if (currentCost + predictedCost <= upperBoundCost) {
                        queue.add(
                            SearchState(
                                currentCost = currentCost.toShort(),
                                predictedCost = predictedCost.toShort(),
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
                    },
                    prevProcessState,
                    nEvents,
                    searchState.getPreviousActivity()
                )//.coerceAtLeast(lastCost - currentCost)
                assert(predictedCost >= lastCost - currentCost)
                if (currentCost + predictedCost <= upperBoundCost) {
                    val state = SearchState(
                        currentCost = currentCost.toShort(),
                        predictedCost = predictedCost.toShort(),
                        activity = null,
                        event = nextEventIndex.toShort(),
                        previousSearchState = searchState,
                        processState = prevProcessState
                    )
                    if (!visited.contains(state)) {
                        queue.add(state)
                    }
                }
            }
        }

        return null
    }

    private fun countRemainingEventsWithTheSameConceptName(events: List<Event>): List<Map<String?, Int>> {
        if (countUnmatchedModelMoves === null)
            return emptyList()

        val nEvents = ArrayList<Map<String?, Int>>()
        val tmp = HashMap<String?, Int>()
        for (e in events.asReversed()) {
            tmp.compute(e.conceptName) { _, old ->
                (old ?: 0) + 1
            }
            nEvents.add(HashMap(tmp))
        }
        nEvents.reverse()

        countUnmatchedModelMoves.reset()

        return nEvents
    }

    private fun formatAlignment(
        searchState: SearchState,
        events: List<Event>
    ): Alignment {
        val steps = ArrayList<Step>()
        var state: SearchState = searchState
        while (state.event != (-1).toShort()) {
            with(state) {
                steps.add(
                    Step(
                        modelMove = activity,
                        modelState = processState,
                        modelCause = cause.orEmpty(),
                        logMove = if (event != SKIP_EVENT) events[event.toInt()] else null,
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

        return Alignment(steps, searchState.currentCost.toInt())
    }

    private fun isSynchronousMove(event: Event?, activity: Activity): Boolean =
        event !== null && event.conceptName == activity.name

    private fun predict(
        events: List<Event>,
        startIndex: Int,
        isFinalState: Ternary,
        prevProcessState: ProcessModelState,
        nEvents: List<Map<String?, Int>>,
        prevActivity: Activity?
    ): Int {
//        if (startIndex == SKIP_EVENT || startIndex >= events.size)
//            return if (isFinalState != Ternary.False) 0 else penalty.modelMove // we reached the end of trace, should we move in the model?

        assert(startIndex in 0..events.size) { "startIndex: $startIndex" }

        var sum = 0
//        if (isFinalState == Ternary.False && endActivities.isNotEmpty()) {
//            var hasEndActivity = false
//            for (index in startIndex until events.size) {
//                if (endActivities.contains(events[index].conceptName)) {
//                    hasEndActivity = true
//                    break
//                }
//            }
//            if (!hasEndActivity)
//                sum += penalty.modelMove
//        }

        val unmatchedLogMovesCount =
            countUnmatchedLogMoves?.compute(startIndex, events, prevProcessState, prevActivity) ?: 0
        sum += unmatchedLogMovesCount * penalty.logMove

        val unmatchedModelMovesCount = countUnmatchedModelMoves?.compute(startIndex, nEvents, prevProcessState) ?: 0

        // Subtract one modelMove, because we are considering previous state and it may have been already included in the cost
        sum += (unmatchedModelMovesCount - 1).coerceAtLeast(0) * penalty.modelMove

        return sum
    }

    private data class SearchState(
        val currentCost: Short,
        /**
         * The predicted cost of reaching the end state.
         * This value must be less than or equal the actual value.
         */
        val predictedCost: Short,
        /**
         * The last executed activity or null if no move in the model has been done.
         */
        val activity: Activity?,
        /**
         * The collection of activities that were the direct cause for [activity].
         */
        var cause: Collection<Activity>? = null,
        /**
         * The last executed event.
         */
        val event: Short,
        val previousSearchState: SearchState?,
        var processState: ProcessModelState? = null
    ) : Comparable<SearchState> {
        private fun calcProcessStateAndCause(instance: ProcessModelInstance) {
            checkNotNull(activity)
            instance.setState(previousSearchState!!.getProcessState(instance).copy())
            val execution = instance.getExecutionFor(activity)
            cause = execution.cause
            execution.execute()
            processState = instance.currentState
        }

        fun getProcessState(instance: ProcessModelInstance): ProcessModelState {
            if (processState === null && activity !== null) {
                calcProcessStateAndCause(instance)
            }
            return processState ?: previousSearchState!!.getProcessState(instance)
        }

        fun getCause(instance: ProcessModelInstance): Collection<Activity> {
            if (cause === null && activity !== null)
                calcProcessStateAndCause(instance)
            return cause!!
        }

        fun getPreviousEventIndex(): Int {
            if (event != SKIP_EVENT)
                return event.toInt()
            if (previousSearchState === null)
                return -1
            return previousSearchState.getPreviousEventIndex()
        }

        fun getPreviousActivity(): Activity? {
            if (activity !== null)
                return activity
            if (previousSearchState === null)
                return null
            return previousSearchState.getPreviousActivity()
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
            var result = currentCost.toInt()
            result = 31 * result + getPreviousEventIndex()
            result = 31 * result + processState.hashCode()
            return result
        }
    }
}
