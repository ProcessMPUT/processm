package processm.conformance.measures.precision

import processm.conformance.measures.Measure
import processm.conformance.models.alignments.AStar
import processm.conformance.models.alignments.AlignerFactory
import processm.conformance.models.alignments.events.ConceptNameEventSummarizer
import processm.conformance.models.antialignments.*
import processm.core.log.hierarchical.Log
import processm.core.log.hierarchical.Trace
import processm.core.models.commons.ProcessModel
import processm.helpers.SameThreadExecutorService

/**
 * The anti-alignment-based precision measure as defined in Definition 7 in B.F. van Dongen,
 * A Unified Approach for Measuring Precision and Generalization Based on Anti-Alignments.
 */
class AntiAlignmentBasedPrecision(
    val model: ProcessModel,
    val alignerFactory: AlignerFactory = AlignerFactory { m, p, _ -> AStar(m, p) },
    val antiAligner: AntiAligner = TwoPhaseDFS(model),
    val alpha: Double = 0.5
) : Measure<Log, Double> {
    init {
        require(alpha in 0.0..1.0)
    }

    override fun invoke(artifact: Log): Double =
        alpha * traceBasedPrecision(artifact.traces) + (1 - alpha) * logBasedPrecision(artifact.traces)

    /**
     * Definition 5.
     */
    internal fun traceBasedPrecision(traces: Sequence<Trace>): Double {
        val logUnique =
            traces.associateByTo(
                HashMap(),
                ConceptNameEventSummarizer::invoke
            )

        var sum = 0.0
        var maxSize = 1
        for (trace in logUnique.values) {
            val size = trace.events.count()
            if (size > maxSize)
                maxSize = size
            val antiAlignments = antiAligner.align(logUnique.values.asSequence().filter { it !== trace }, size)
            sum += antiAlignments.maxOf { d(trace, it) }
        }
        sum /= maxSize * logUnique.size
        assert(sum in 0.0..1.0)
        return 1.0 - sum
    }

    /**
     * Definition 6.
     */
    internal fun logBasedPrecision(traces: Sequence<Trace>): Double {
        val n = 2 * traces.maxOf { it.events.count() }
        val antiAlignments = antiAligner.align(traces, n)
        val imprecision = antiAlignments.maxOf { it.cost }.toDouble() / antiAlignments.maxOf { it.size }
        assert(imprecision in 0.0..1.0)
        return 1.0 - imprecision
    }

    private fun d(trace: Trace, antiAlignment: AntiAlignment): Int {
        val replay = ReplayModel(antiAlignment.steps.mapNotNull { it.modelMove })
        val aligner = alignerFactory(replay, antiAligner.penalty, SameThreadExecutorService)
        val alignment = aligner.align(trace)
        return alignment.cost
    }
}
