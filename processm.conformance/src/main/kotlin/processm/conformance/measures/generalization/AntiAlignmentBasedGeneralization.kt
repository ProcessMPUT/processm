package processm.conformance.measures.generalization

import processm.conformance.measures.Measure
import processm.conformance.models.alignments.AStar
import processm.conformance.models.alignments.AlignerFactory
import processm.conformance.models.alignments.events.ConceptNameEventSummarizer
import processm.conformance.models.antialignments.AntiAligner
import processm.conformance.models.antialignments.AntiAlignment
import processm.conformance.models.antialignments.TwoPhaseDFS
import processm.conformance.models.antialignments.size
import processm.core.helpers.SameThreadExecutorService
import processm.core.log.hierarchical.Log
import processm.core.log.hierarchical.Trace
import processm.core.models.commons.Activity
import processm.core.models.commons.ProcessModel
import processm.core.models.commons.ProcessModelState
import kotlin.math.hypot

/**
 * The anti-alignment-based precision measure as defined in Definition 7 in B.F. van Dongen,
 * A Unified Approach for Measuring Precision and Generalization Based on Anti-Alignments.
 */
class AntiAlignmentBasedGeneralization(
    val model: ProcessModel,
    val alignerFactory: AlignerFactory = AlignerFactory { m, p, _ -> AStar(m, p) },
    val antiAligner: AntiAligner = TwoPhaseDFS(model),
    val alpha: Double = 0.5
) : Measure<Log, Double> {
    init {
        require(alpha in 0.0..1.0)
    }

    override fun invoke(artifact: Log): Double =
        // Definition 11
        alpha * traceBasedGeneralization(artifact.traces) + (1 - alpha) * logBasedGeneralization(artifact.traces)

    /**
     * Definition 9
     */
    internal fun traceBasedGeneralization(traces: Sequence<Trace>): Double {
        val logUnique = HashMap<List<String?>, Pair<Trace, Int>>()
        for (trace in traces)
            logUnique.compute(ConceptNameEventSummarizer(trace)) { _, old ->
                if (old === null)
                    trace to 1
                else
                    old.first to old.second + 1
            }

        var sum = 0.0
        var sumWeights = 0.0
        for ((key, value) in logUnique) {
            val (trace, weight) = value

            val size = key.size
            val filteredLog = logUnique.values.asSequence().mapNotNull { if (it.first !== trace) it.first else null }
            val antiAlignments = antiAligner.align(filteredLog, size)
            val d = antiAlignments.maxOf { it.cost }.toDouble() / size
            val drec = antiAlignments.minOf { drec(it) }
            val generalization = 1.0 - hypot(1.0 - d, drec).coerceAtMost(1.0)
            sum += weight * generalization

            sumWeights += weight
        }

        return sum / sumWeights
    }

    /**
     * Definition 10
     */
    internal fun logBasedGeneralization(traces: Sequence<Trace>): Double {
        val size = 2 * traces.maxOf { it.events.count() }
        val antiAlignments = antiAligner.align(traces, size)
        val d = antiAlignments.maxOf { it.cost }.toDouble() / antiAlignments.maxOf { it.size }
        val drec = antiAlignments.minOf { drec(it) }
        return 1.0 - hypot(1.0 - d, drec).coerceAtMost(1.0)
    }

    /**
     * Definition 8
     */
    private fun drec(antiAlignment: AntiAlignment): Double {

        val modelStates = antiAlignment.steps.mapNotNullTo(HashSet()) { it.modelState }
        val aligner = alignerFactory(model, antiAligner.penalty, SameThreadExecutorService)
        val alignment = aligner.align(Trace(events = antiAlignment.steps.asSequence().mapNotNull { it.logMove }))
        val synchronousStates = alignment.steps.mapNotNullTo(HashSet()) { it.modelState }

        var numerator = 0
        for (state in modelStates) {
            val recovery = findRecoverySequenceSize(state, synchronousStates)
            if (recovery > numerator)
                numerator = recovery
        }

        return numerator.toDouble() / (antiAlignment.size - 1)
    }

    private fun findRecoverySequenceSize(
        initialState: ProcessModelState,
        finalStates: Set<ProcessModelState>
    ): Int {
        val instance = model.createInstance()
        val execInstance = model.createInstance()

        val stack = ArrayDeque<SearchState>()
        stack.addLast(
            SearchState(
                activity = null,
                processState = initialState
            )
        )

        var best: Int = Int.MAX_VALUE

        while (stack.isNotEmpty()) {
            val searchState = stack.removeLast()
            val state = searchState.processState

            if (state in finalStates) {
                var result = 0
                var s = searchState
                while (s.previous !== null) {
                    if (!s.activity!!.isArtificial)
                        result += 1
                    s = s.previous!!
                }

                if (best > result)
                    best = result

                continue
            }

            instance.setState(state)

            for (activity in instance.availableActivities) {
                execInstance.setState(state.copy())
                val execution = execInstance.getExecutionFor(activity)
                execution.execute()
                stack.addLast(
                    SearchState(
                        activity = activity,
                        processState = execInstance.currentState,
                        previous = searchState
                    )
                )
            }
        }

        return checkNotNull(best) { "A final state is not reachable." }
    }

    private data class SearchState(
        val activity: Activity?,
        val processState: ProcessModelState,
        val previous: SearchState? = null
    )
}
