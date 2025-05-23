package processm.conformance.models.antialignments

import processm.conformance.models.alignments.*
import processm.conformance.models.alignments.cache.Cache
import processm.conformance.models.alignments.events.EventsSummarizer
import processm.core.log.Event
import processm.core.log.hierarchical.Trace
import processm.core.models.commons.Activity
import processm.core.models.commons.ProcessModel
import processm.core.models.commons.ProcessModelState
import processm.helpers.SameThreadExecutorService
import java.util.*
import kotlin.collections.ArrayDeque
import kotlin.math.max

/**
 * @property alignerFactory The factory that produces [Aligner] to align model traces with log. The produced [Aligner]
 * MUST NOT use [processm.conformance.models.alignments.cache.AlignmentCache]. This class ensures that duplicate traces
 * are discarded, hence cache would not bring performance gain. On the other hand, the underlying model implementation
 * is mutable and a change in the model would invalidate cache. The cache invalidation is not supported by the
 * [processm.conformance.models.alignments.cache.AlignmentCache] interface.
 */
class TwoPhaseDFS(
    override val model: ProcessModel,
    override val penalty: PenaltyFunction = PenaltyFunction(),
    val alignerFactory: AlignerFactory = AlignerFactory { mod, pen, _ ->
        NeedlemanWunschAligner(mod as ReplayModel, pen)
    }
) : AntiAligner {

    @OptIn(ResettableAligner::class)
    override fun align(
        log: Sequence<Trace>,
        size: Int,
        eventsSummarizer: EventsSummarizer<*>
    ): List<AntiAlignment> {
        require(size >= 1) { "Size must be positive." }

        val logUnique =
            log.associateByTo(HashMap(), eventsSummarizer::invoke) { trace -> trace to trace.events.toList() }

        // maximal, complete anti-alignments
        val antiAlignments = ArrayList<AntiAlignment>()
        var globalCost = -1 // maximize
        val perTraceAntiAlignments = ArrayList<AntiAlignment>()
        val lcsCache = IntArray((size + 1) * (logUnique.values.maxOf { it.second.size } + 1))

        val replayModel = ReplayModel(model.activities.toList())
        val aligner = alignerFactory(replayModel, penalty, SameThreadExecutorService)
        main@ for ((modelTrace, modelStates) in modelTraces(size)) {
            val matchingLogTrace = logUnique[eventsSummarizer.summary(modelTrace)]
            if (matchingLogTrace !== null) {
                if (0 >= globalCost) {
                    globalCost = 0
                    replayModel.trace = modelTrace
                    aligner.reset()
                    val alignment = aligner.align(matchingLogTrace.first, 0)!!
                    assert(alignment.cost == 0)
                    antiAlignments.add(mapStates(alignment, modelStates))
                }
                continue
            }

            val modelTraceNoSilent = modelTrace.filter { !it.isSilent }
            val costEstimateUB = logUnique.values.minOf { logTrace ->
                val events = logTrace.second
                val lcs = longestCommonSubseq(modelTraceNoSilent, events, cache = lcsCache.also { Arrays.fill(it, -1) })
                (modelTrace.size - lcs) * penalty.modelMove + (events.size - lcs) * penalty.logMove + lcs * penalty.synchronousMove
            }


            if (costEstimateUB < globalCost)
                continue

            // globalCost >= costEstimateUB

            replayModel.trace = modelTrace
            aligner.reset()
            var perTraceCost = costEstimateUB // minimize
            perTraceAntiAlignments.clear()
            for (logTrace in logUnique.values) {
                val alignment = aligner.align(logTrace.first, perTraceCost)
                if (alignment === null)
                    continue
                if (alignment.cost < perTraceCost) {
                    if (alignment.cost < globalCost)
                        continue@main
                    // globalCost <= alignment.cost < perTraceCost <= costEstimateUB
                    perTraceCost = alignment.cost
                    perTraceAntiAlignments.clear()
                    perTraceAntiAlignments.add(mapStates(alignment, modelStates))
                } else if (alignment.cost == perTraceCost) {
                    assert(alignment.cost >= globalCost)
                    //assert(perTraceAntiAlignments.all { it.cost == alignment.cost })
                    perTraceAntiAlignments.add(mapStates(alignment, modelStates))
                }
            }

            // globalCost <= perTraceCost = min(alignment.cost) <= costEstimateUB

            if (perTraceCost > globalCost) {
                globalCost = perTraceCost
                antiAlignments.clear()
                antiAlignments.addAll(perTraceAntiAlignments)
            } else {
                assert(perTraceCost == globalCost)
                //assert(antiAlignments.all { it.cost == perTraceAntiAlignments.first().cost })
                antiAlignments.addAll(perTraceAntiAlignments)
            }
        }

        check(antiAlignments.isNotEmpty()) { "An anti-alignment within the limit of $size events does not exist." }

        return antiAlignments
    }

    private fun longestCommonSubseq(
        x: List<Activity>,
        y: List<Event>,
        m: Int = x.size,
        n: Int = y.size,
        maxN: Int = n + 1,
        cache: IntArray = IntArray((m + 1) * (n + 1) - 1).also { Arrays.fill(it, -1) }
    ): Int {
        fun computeIfAbsent(m: Int, n: Int): Int {
            val key = m * maxN + n
            var value = cache[key]
            if (value < 0) {
                value = longestCommonSubseq(x, y, m, n, maxN, cache)
                cache[key] = value
            }
            return value
        }

        if (m == 0 || n == 0)
            return 0

        val m1 = m - 1
        val n1 = n - 1
        if (x[m1].name.equals(y[n1].conceptName))
            return 1 + computeIfAbsent(m1, n1)

        val lcs1 = computeIfAbsent(m, n1)
        // lcs1 <= m && lcs1 <= n1 = n - 1
        // lcs2 <= m1 = m - 1 && lcs2 <= n
        if (lcs1 >= m1 || lcs1 >= n)
            return lcs1

        val lcs2 = computeIfAbsent(m1, n)
        return max(lcs1, lcs2)
    }

    private fun modelTraces(maxSize: Int): Sequence<Pair<List<Activity>, List<ProcessModelState>>> = sequence {
        val cache = Cache<List<Activity>>()

        val instance = model.createInstance()
        val execInstance = model.createInstance()
        val stack = ArrayDeque<SearchState>()
        stack.addLast(
            SearchState(
                activity = null,
                state = instance.currentState,
                size = 0
            )
        )

        while (stack.isNotEmpty()) {
            val searchState = stack.removeLast()
            instance.setState(searchState.state)

            if (instance.isFinalState) {
                val trace = ArrayList<Activity>()
                val states = ArrayList<ProcessModelState>()
                var s = searchState
                while (s.previous !== null) {
                    if (!s.activity!!.isArtificial) {
                        trace.add(s.activity!!)
                        states.add(s.state)
                    }
                    s = s.previous!!
                }

                if (trace.isEmpty())
                    continue // e.g., a trace containing only silent activities

                if (!cache.add(trace))
                    continue

                states.add(s.state) // the initial state

                trace.reverse()
                states.reverse()
                yield(trace to states)

                continue
            }

            assert(searchState.state === instance.currentState)
            for (activity in instance.availableActivities) {

                if (searchState.size >= maxSize && !activity.isSilent)
                    continue

                execInstance.setState(searchState.state.copy())
                val execution = execInstance.getExecutionFor(activity)

                execution.execute()
                val newSearchState = SearchState(
                    activity = execution.activity,
                    state = execInstance.currentState,
                    size = searchState.size + (if (activity.isSilent) 0 else 1),
                    previous = searchState
                )

                stack.addLast(newSearchState)
            }
        }
    }

    private fun mapStates(alignment: Alignment, states: List<ProcessModelState>): Alignment = alignment.copy(
        steps = alignment.steps.map { s ->
            if (s.modelState === null) s
            else s.copy(modelState = states[(s.modelState as ReplayModelState).index])
        }
    )

    private class SearchState(
        /**
         * The last executed activity that led to [state]. null for the initial state.
         */
        val activity: Activity?,
        /**
         * The process model state caused by execution of [activity].
         */
        val state: ProcessModelState,
        val size: Int,
        val previous: SearchState? = null
    )
}
