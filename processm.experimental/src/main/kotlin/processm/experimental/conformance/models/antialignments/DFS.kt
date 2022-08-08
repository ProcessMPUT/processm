package processm.experimental.conformance.models.antialignments

import processm.conformance.models.DeviationType
import processm.conformance.models.alignments.PenaltyFunction
import processm.conformance.models.alignments.Step
import processm.conformance.models.alignments.events.EventsSummarizer
import processm.conformance.models.antialignments.AntiAligner
import processm.conformance.models.antialignments.AntiAlignment
import processm.core.log.Event
import processm.core.log.hierarchical.Trace
import processm.core.models.commons.Activity
import processm.core.models.commons.ProcessModel
import processm.core.models.commons.ProcessModelInstance
import processm.core.models.commons.ProcessModelState

class DFS(override val model: ProcessModel, override val penalty: PenaltyFunction = PenaltyFunction()) : AntiAligner {
    override fun align(
        log: Sequence<Trace>,
        size: Int,
        eventsSummarizer: EventsSummarizer<*>
    ): Collection<AntiAlignment> {
        require(size >= 1) { "Size must be positive." }

        val traces = log.distinctBy(eventsSummarizer::invoke).map { t -> t.events.toList() }

        val instance = model.createInstance()
        val best =
            HashMap<List<Activity>, AntiAlignment>() // maximize size, for every model trace minimize cost; finally pick the anti-alignment with the largest cost

        val stack = ArrayDeque<SearchState>()
        stack.addLast(
            SearchState(
                size = 0,
                cost = 0,
                activity = null,
                logState = traces.associateWith { 0 },
                type = DeviationType.None,
                processState = instance.currentState.copy(),
            )
        )

        while (stack.isNotEmpty()) {
            val prevSearchState = stack.removeLast()
            val prevProcessState = prevSearchState.getProcessState(instance)
            instance.setState(prevProcessState)

            if (instance.isFinalState) {
                val (trace, pos) = prevSearchState.logState.minBy { (trace, pos) -> trace.size - pos }
                assert(pos <= trace.size)
                if (trace.size == pos) {
                    // we found anti-alignment
                    best.compute(prevSearchState.asSequence().mapNotNullTo(ArrayList()) { it.activity }) { _, old ->
                        if (old === null || old.cost > prevSearchState.cost)
                            formatAntiAlignment(prevSearchState)
                        else
                            old
                    }
                }
            }

            assert(prevSearchState.logState.isNotEmpty())
            assert(instance.currentState === prevProcessState)
            for (activity in instance.availableActivities) {
                // silent activities are special
                if (activity.isSilent) {
                    if (activity.isArtificial) {
                        // just move the state of the model without moving in the log
                        instance.setState(prevProcessState.copy())
                        instance.getExecutionFor(activity).execute()
                        val state = prevSearchState.copy(
                            processState = instance.currentState
                        )
                        stack.addLast(state)
                    } else {
                        stack.addLast(
                            prevSearchState.copy(
                                cost = prevSearchState.cost + penalty.silentMove,
                                activity = activity,
                                type = DeviationType.ModelDeviation,
                                processState = null,
                                previousSearchState = prevSearchState
                            )
                        )
                    }
                    continue
                }

                assert(!activity.isSilent)

                if (prevSearchState.size < size) {
                    // search for synchronous and model-only moves; all at once
                    val prevLogOnly = prevSearchState.asSequence()
                        .takeWhile { it.type == DeviationType.LogDeviation }
                        .flatMapTo(HashSet()) { it.logState.mapNotNull { (t, p) -> t[p - 1].conceptName } }
                    val synchronous = HashMap<List<Event>, Int>()
                    val modelOnly = HashMap<List<Event>, Int>()
                    for ((trace, pos) in prevSearchState.logState) {
                        if (trace.size > pos && isSynchronousMove(trace[pos], activity)) {
                            synchronous[trace] = pos + 1
                        } else if (activity.name !in prevLogOnly) {
                            modelOnly[trace] = pos
                        }
                    }

                    // add synchronous moves if applicable; all at once
                    if (synchronous.isNotEmpty()) {
                        stack.addLast(
                            SearchState(
                                size = prevSearchState.size + 1,
                                cost = prevSearchState.cost + penalty.synchronousMove,
                                activity = activity,
                                logState = synchronous,
                                type = DeviationType.None,
                                previousSearchState = prevSearchState
                            )
                        )
                    }

                    // add model-only moves; all at once
                    if (modelOnly.isNotEmpty()) {
                        stack.addLast(
                            SearchState(
                                size = prevSearchState.size + 1,
                                cost = prevSearchState.cost + penalty.modelMove,
                                activity = activity,
                                logState = modelOnly,
                                type = DeviationType.ModelDeviation,
                                previousSearchState = prevSearchState
                            )
                        )
                    }
                }
            }

            // add log-only moves; all at once
            val prevModelMoves = prevSearchState.asSequence()
                .takeWhile { it.type == DeviationType.ModelDeviation }
                .mapTo(HashSet()) { it.activity!!.name }
            val logOnly = HashMap<List<Event>, Int>()
            for ((trace, pos) in prevSearchState.logState) {
                if (trace.size > pos + 1 && trace[pos].conceptName !in prevModelMoves) {
                    logOnly[trace] = pos + 1
                }
            }
            if (logOnly.isNotEmpty()) {
                stack.addLast(
                    SearchState(
                        size = prevSearchState.size,
                        cost = prevSearchState.cost + penalty.logMove,
                        activity = null,
                        logState = logOnly,
                        type = DeviationType.LogDeviation,
                        previousSearchState = prevSearchState
                    )
                )
            }
        }

        println(best.values)

        check(best.isNotEmpty()) {
            "Cannot align the log with the model. The final state of the model is not reachable within the limit of $size moves."
        }

        val maxCost = best.values.maxOf { it.cost }
        best.values.removeIf { it.cost < maxCost }
        return best.values
    }

    private fun isSynchronousMove(event: Event?, activity: Activity): Boolean =
        event !== null && event.conceptName == activity.name

    private fun formatAntiAlignment(state: SearchState): AntiAlignment {
        val steps = ArrayList<Step>()
        var (trace, pos) = state.logState.minBy { (trace, pos) -> trace.size - pos }
        assert(trace.size == pos)
        for (state in state.asSequence()) {
            if (state.previousSearchState === null)
                break

            steps.add(
                Step(
                    modelMove = state.activity,
                    modelState = state.processState,
                    logMove = if (state.type != DeviationType.ModelDeviation) trace[pos - 1] else null,
                    logState = trace.subList(0, pos).asSequence(),
                    type = state.type
                )
            )

            if (state.type != DeviationType.ModelDeviation)
                pos -= 1
        }

        steps.reverse()

        return AntiAlignment(steps, state.cost)
    }

    private data class SearchState(
        /**
         * The total number of non-silent model moves.
         */
        val size: Int,
        /**
         * The total cost of the anti-alignment w.r.t. the penalty function.
         */
        val cost: Int,
        /**
         * The last executed activity or null if no move in the model has been done.
         */
        val activity: Activity?,

        /**
         * The position of the next event to process for each trace.
         * Key: trace, value: position.
         */
        val logState: Map<List<Event>, Int>,

        val type: DeviationType,

        var processState: ProcessModelState? = null,
        val previousSearchState: SearchState? = null,
    ) {
        fun getProcessState(instance: ProcessModelInstance): ProcessModelState {
            if (processState === null && activity !== null) {
                instance.setState(previousSearchState!!.getProcessState(instance).copy())
                instance.getExecutionFor(activity).execute()
                processState = instance.currentState
            }
            return processState ?: previousSearchState!!.getProcessState(instance)
        }

        fun asSequence(): Sequence<SearchState> = sequence {
            var current: SearchState? = this@SearchState
            do {
                yield(current!!)
                current = current.previousSearchState
            } while (current !== null)
        }
    }
}


