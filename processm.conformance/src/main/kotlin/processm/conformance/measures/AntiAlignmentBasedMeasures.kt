package processm.conformance.measures

import processm.conformance.models.alignments.AStar
import processm.conformance.models.alignments.AlignerFactory
import processm.conformance.models.alignments.events.ConceptNameEventSummarizer
import processm.conformance.models.antialignments.*
import processm.core.log.hierarchical.Log
import processm.core.log.hierarchical.Trace
import processm.core.models.commons.Activity
import processm.core.models.commons.ProcessModel
import processm.core.models.commons.ProcessModelState
import processm.core.models.metadata.URN
import processm.helpers.SameThreadExecutorService
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.math.hypot

data class PrecisionGeneralization(val precision: Double, val generalization: Double)

/**
 * The anti-alignment-based generalization and precision measures as defined in B.F. van Dongen,
 * A Unified Approach for Measuring Precision and Generalization Based on Anti-Alignments.
 *
 *
 */
class AntiAlignmentBasedMeasures(
    val model: ProcessModel,
    val alignerFactory: AlignerFactory = AlignerFactory { m, p, _ -> AStar(m, p) },
    val antiAlignerFactory: (model: ProcessModel) -> AntiAligner = { TwoPhaseDFS(model) },
    val alphaPrecision: Double = 0.5,
    val alphaGeneralization: Double = alphaPrecision,
    val pool: ExecutorService = Executors.newCachedThreadPool()
) : Measure<Log, PrecisionGeneralization> {

    companion object {
        val URN: URN = URN("urn:processm:measures/anti_alignment_based_precision_and_generalization")
    }

    init {
        require(alphaPrecision in 0.0..1.0)
        require(alphaGeneralization in 0.0..1.0)
    }

    override val URN: URN
        get() = AntiAlignmentBasedMeasures.URN

    override fun invoke(artifact: Log): PrecisionGeneralization {
        prepare(artifact.traces)
        val logBasedFuture = pool.submit { logBased(artifact.traces) }
        val traceBasedFuture = pool.submit { traceBased() }
        logBasedFuture.get()
        traceBasedFuture.get()
        logUnique.clear()
        return PrecisionGeneralization(
            // Definition 7
            precision = alphaPrecision * traceBasedPrecision + (1 - alphaPrecision) * logBasedPrecision,
            // Definition 11
            generalization = alphaGeneralization * traceBasedGeneralization + (1 - alphaGeneralization) * logBasedGeneralization
        )
    }

    private var logUnique = HashMap<List<String?>, Pair<Trace, Int>>()
    internal var traceBasedGeneralization: Double = Double.NaN
    internal var traceBasedPrecision: Double = Double.NaN
    internal var logBasedGeneralization: Double = Double.NaN
    internal var logBasedPrecision: Double = Double.NaN

    private fun prepare(traces: Sequence<Trace>) {
        for (trace in traces)
            logUnique.compute(ConceptNameEventSummarizer(trace)) { _, old ->
                if (old === null)
                    trace to 1
                else
                    old.first to old.second + 1
            }
    }


    /**
     * Definitions 6 and 10
     */
    private fun logBased(traces: Sequence<Trace>) {
        val antiAligner = antiAlignerFactory(model)
        val n = 2 * traces.maxOf { it.events.count() }
        val antiAlignments = antiAligner.align(traces, n)
        val imprecision = antiAlignments.maxOf { it.cost }.toDouble() / antiAlignments.maxOf { it.size }
        assert(imprecision in 0.0..1.0)
        val drec = antiAlignments.minOf { drec(antiAligner, it) }
        logBasedGeneralization = 1.0 - hypot(1.0 - imprecision, drec).coerceAtMost(1.0)
        logBasedPrecision = 1.0 - imprecision
    }

    /**
     * Definitions 5 and 9
     */
    private fun traceBased() {
        val antiAligner = antiAlignerFactory(model)
        var generalizationNom = 0.0
        var generalizationDen = 0.0
        var precision = 0.0
        var maxSize = 1
        for ((key, value) in logUnique) {
            val (trace, weight) = value

            val size = key.size
            if (size > maxSize)
                maxSize = size
            val filteredLog = logUnique.values.asSequence().mapNotNull { if (it.first !== trace) it.first else null }
            val antiAlignments = antiAligner.align(filteredLog, size)
            val d = antiAlignments.maxOf { it.cost }.toDouble() / size
            val drec = antiAlignments.minOf { drec(antiAligner, it) }
            val generalization = 1.0 - hypot(1.0 - d, drec).coerceAtMost(1.0)
            generalizationNom += weight * generalization
            precision += antiAlignments.maxOf { d(antiAligner, trace, it) }

            generalizationDen += weight
        }
        this.traceBasedGeneralization = generalizationNom / generalizationDen
        precision /= maxSize * logUnique.size
        assert(precision in 0.0..1.0)
        this.traceBasedPrecision = 1.0 - precision
    }


    /**
     * Definition 8
     */
    private fun drec(antiAligner: AntiAligner, antiAlignment: AntiAlignment): Double {

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


    private fun d(antiAligner: AntiAligner, trace: Trace, antiAlignment: AntiAlignment): Int {
        val replay = ReplayModel(antiAlignment.steps.mapNotNull { it.modelMove })
        val aligner = alignerFactory(replay, antiAligner.penalty, SameThreadExecutorService)
        val alignment = aligner.align(trace)
        return alignment.cost
    }
}
