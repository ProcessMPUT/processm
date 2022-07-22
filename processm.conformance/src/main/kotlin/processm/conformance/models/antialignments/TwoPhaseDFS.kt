package processm.conformance.models.antialignments

import processm.conformance.models.alignments.AStar
import processm.conformance.models.alignments.Aligner
import processm.conformance.models.alignments.AlignerFactory
import processm.conformance.models.alignments.PenaltyFunction
import processm.conformance.models.alignments.events.EventsSummarizer
import processm.core.helpers.SameThreadExecutorService
import processm.core.log.hierarchical.Trace
import processm.core.models.commons.Activity
import processm.core.models.commons.ProcessModel
import processm.core.models.commons.ProcessModelState

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
        AStar(
            mod,
            pen,
            CountUnmatchedReplayModelMoves(mod as ReplayModel)
        )
    }
) : AntiAligner {

    override fun align(
        log: Sequence<Trace>,
        size: Int,
        eventsSummarizer: EventsSummarizer<*>
    ): List<AntiAlignment> {
        require(size >= 1) { "Size must be positive." }

        val logUnique = log.distinctBy(eventsSummarizer::invoke)

        // maximal, complete anti-alignments
        val antiAlignments = ArrayList<AntiAlignment>()
        var globalCost = -1 // maximize
        val perTraceAntiAlignments = ArrayList<AntiAlignment>()

        val replayModel = ReplayModel(model.activities.toList())
        val aligner = alignerFactory(replayModel, penalty, SameThreadExecutorService)
        main@ for (modelTrace in modelTraces(size)) {
            //println(modelTrace.map { it.name })
            replayModel.trace = modelTrace
            var perTraceCost = Int.MAX_VALUE // minimize
            for (alignment in aligner.align(logUnique)) {
                if (alignment.cost < perTraceCost) {
                    if (alignment.cost < globalCost)
                        continue@main
                    perTraceCost = alignment.cost
                    perTraceAntiAlignments.clear()
                    perTraceAntiAlignments.add(alignment)
                } else if (alignment.cost == perTraceCost) {
                    perTraceAntiAlignments.add(alignment)
                }
            }

            if (perTraceCost > globalCost) {
                globalCost = perTraceCost
                antiAlignments.clear()
                antiAlignments.addAll(perTraceAntiAlignments)
            } else if (perTraceCost == globalCost) {
                antiAlignments.addAll(perTraceAntiAlignments)
            }
        }

        check(antiAlignments.isNotEmpty()) { "An anti-alignment within the limit of $size events does not exist." }

        return antiAlignments
    }

    private fun modelTraces(maxSize: Int): Sequence<List<Activity>> = sequence {
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
                var s = searchState
                while (s.previous !== null) {
                    if (!s.activity!!.isSilent)
                        trace.add(s.activity!!)
                    s = s.previous!!
                }
                trace.reverse()

                yield(trace)

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
