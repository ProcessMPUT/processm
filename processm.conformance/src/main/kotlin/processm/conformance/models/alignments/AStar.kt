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
import java.util.*
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
import kotlin.concurrent.write


/**
 * An A*-based calculator of alignments. The implementation is based on the priority queue, the naive heuristics, and
 * DFS-based second-order queuing criterion as described in Sebastiaan J. van Zelst, Alfredo Bolt, and
 * Boudewijn F. van Dongen, Tuning Alignment Computation: An Experimental Evaluation.
 * It is model-representation agnostic. It works for all model representations and does not convert them to Petri net
 * (like in the above-mentioned paper).
 *
 * This class is thread-safe.
 */
@OptIn(ResettableAligner::class)
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
        private const val INITIAL_SHORTEST_PATH = 1 shl 24
    }

    private val activities: Set<String?> =
        model.activities.mapNotNullTo(HashSet()) { if (it.isSilent) null else it.name }

    private val endActivities: Set<String?> =
        model.endActivities.mapNotNullTo(HashSet()) { if (it.isSilent) null else it.name }

    private val visited = ThreadLocal.withInitial { Cache<SearchState>() }

    /**
     * The minimal known count of non-silent moves in the [model]. This property is updated when a new alignment is found.
     */
    private var shortestPathInModel = INITIAL_SHORTEST_PATH
    private var shortestPathInModelSilentMoves = INITIAL_SHORTEST_PATH
    private val shortestPathLock = ReentrantReadWriteLock()

    /**
     * Statistics for tests only.
     */
    @Deprecated("This property is not thread safe and concurrent runs of align() may yield unpredictable results. Do not use, except in the single-threaded tests.")
    internal var visitedStatesCount: Int = 0
        private set

    override fun reset() {
        shortestPathLock.write {
            shortestPathInModel = INITIAL_SHORTEST_PATH
            shortestPathInModelSilentMoves = INITIAL_SHORTEST_PATH
        }
    }

    init {
        reset()
    }

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
        val labeledModelMoves = alignment?.steps?.count { it.modelMove?.isSilent == false } ?: Int.MAX_VALUE
        val silentModelMoves = alignment?.steps?.count { it.modelMove?.isSilent == true } ?: Int.MAX_VALUE
        shortestPathLock.write {
            if (labeledModelMoves < shortestPathInModel) {
                shortestPathInModel = labeledModelMoves
                shortestPathInModelSilentMoves = silentModelMoves
            } else if (labeledModelMoves == shortestPathInModel && silentModelMoves < shortestPathInModelSilentMoves) {
                shortestPathInModelSilentMoves = silentModelMoves
            }
        }
        if (events.size == eventsWithExistingActivities.size)
            return alignment
        return alignment?.fillMissingEvents(events, penalty)?.also { if (it.cost > costUpperBound) return@align null }
    }

    private fun alignInternal(events: List<Event>, costLimit: Int): Alignment? {
        countUnmatchedLogMoves?.reset()
        val nEvents = countRemainingEventsWithTheSameConceptName(events)
        val thread = Thread.currentThread()
        val queue = PriorityQueue<SearchState>(QUEUE_INITIAL_CAP)
        val visited = this.visited.get()

        val instance = model.createInstance()
        val initialProcessState = instance.currentState
        val initialSearchState = SearchState(
            currentCost = 0,
            predictedCost = predict(
                events,
                0,
                initialProcessState,
                nEvents,
                null
            ).toShort(),
            activity = null, // before first activity
            event = -1, // before first event
            previousSearchState = null,
            processState = initialProcessState
        )
        val firstEvent = events.firstOrNull()
        val lastEvent = events.lastOrNull()
        val matchingStartEnd = (if (model.startActivities.any { isSynchronousMove(firstEvent, it) }) 1 else 0) +
                (if (model.endActivities.any { isSynchronousMove(lastEvent, it) }) 1 else 0)
        val upperBoundCostModelPart = shortestPathLock.read {
            (shortestPathInModel - matchingStartEnd).coerceAtLeast(0) * penalty.modelMove +
                    shortestPathInModelSilentMoves * penalty.silentMove
        }
        val upperBoundCostLogPart = (events.size - matchingStartEnd).coerceAtLeast(0) * penalty.logMove
        var upperBoundCost = minOf(upperBoundCostModelPart + upperBoundCostLogPart, costLimit)
        if (initialSearchState.predictedCost <= upperBoundCost)
            queue.add(initialSearchState)

        try {
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
                // This condition imposes order of model-only moves before log-only moves, as they are independent
                // and otherwise can be run in any order increasing so the total number of states.
                val canMoveInModelOnly = searchState.activity !== null || searchState.event == (-1).toShort()

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
                        } else if (canMoveInModelOnly) {
                            val currentCost = searchState.currentCost + penalty.silentMove
                            val predictedCost = predict(events, nextEventIndex, prevProcessState, nEvents, activity)
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
                    val synchronousAvailable = isSynchronousMove(nextEvent, activity)
                    if (synchronousAvailable) {
                        val currentCost = searchState.currentCost + penalty.synchronousMove
                        val predictedCost =
                            predict(
                                events,
                                nextEventIndex + 1,
                                searchState.processState!!,
                                nEvents,
                                activity
                            )
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
                    if (!synchronousAvailable && canMoveInModelOnly) {
                        val currentCost = searchState.currentCost + penalty.modelMove
                        val predictedCost =
                            predict(events, nextEventIndex, searchState.processState!!, nEvents, activity)
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
                    val predictedCost = predict(events, nextEventIndex + 1, prevProcessState, nEvents, null)
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
                        queue.add(state)
                    }
                }
            }

            return null
        } finally {
            visited.clear()
        }
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
        val instance = model.createInstance()
        while (state.event != (-1).toShort()) {
            with(state) {
                steps.add(
                    Step(
                        modelMove = activity,
                        modelState = processState,
                        modelCause = getCause(instance).asList(),
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
        prevProcessState: ProcessModelState,
        nEvents: List<Map<String?, Int>>,
        curActivity: Activity?
    ): Int {
        assert(startIndex in 0..events.size) { "startIndex: $startIndex" }

        var sum = 0

        val unmatchedLogMovesCount =
            countUnmatchedLogMoves?.compute(startIndex, events, prevProcessState, curActivity) ?: 0
        sum += unmatchedLogMovesCount * penalty.logMove

        val unmatchedModelMovesCount =
            countUnmatchedModelMoves?.compute(startIndex, nEvents, prevProcessState, curActivity) ?: 0
        sum += unmatchedModelMovesCount * penalty.modelMove

        assert(sum >= 0)
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
         * The last executed event.
         */
        val event: Short,
        val previousSearchState: SearchState?,
        var processState: ProcessModelState? = null
    ) : Comparable<SearchState> {
        private fun calcProcessState(instance: ProcessModelInstance) {
            checkNotNull(activity)
            instance.setState(previousSearchState!!.getProcessState(instance).copy())
            val execution = instance.getExecutionFor(activity)
            execution.execute()
            processState = instance.currentState
        }

        fun getProcessState(instance: ProcessModelInstance): ProcessModelState {
            if (processState === null && activity !== null) {
                calcProcessState(instance)
            }
            return processState ?: previousSearchState!!.getProcessState(instance)
        }

        fun getCause(instance: ProcessModelInstance): Array<out Activity> {
            activity ?: return emptyArray()
            instance.setState(previousSearchState!!.getProcessState(instance))
            val execution = instance.getExecutionFor(activity)
            return execution.cause
        }

        fun getPreviousEventIndex(): Int {
            if (event != SKIP_EVENT)
                return event.toInt()
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
                (activity === null) != (other.activity === null) -> (activity === null).compareTo(other.activity === null) // prefer moves with activities
                activity !== null && activity.isSilent != other.activity!!.isSilent ->
                    other.activity.isSilent.compareTo(activity.isSilent) // prefer silent activities
                event < 0 && other.event >= 0 -> 1 // prefer moves with events
                event >= 0 && other.event < 0 -> -1
                else -> 0
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
