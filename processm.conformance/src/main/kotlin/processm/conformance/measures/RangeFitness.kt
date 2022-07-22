package processm.conformance.measures

import processm.conformance.models.alignments.AStar
import processm.conformance.models.alignments.Alignment
import processm.conformance.models.alignments.events.DefaultEventsSummarizer
import processm.conformance.models.alignments.events.EventsSummarizer
import processm.conformance.models.alignments.petrinet.DecompositionAligner
import processm.core.helpers.asCollection
import processm.core.log.hierarchical.Log
import processm.core.log.hierarchical.Trace
import java.util.concurrent.ExecutorCompletionService
import java.util.concurrent.TimeUnit

/**
 * Range fitness gives a lower and an upper bound for the fitness value. Based on the ideas described in
 * Wai Lam Jonathan Lee, H.M.W. Verbeek, Jorge Munoz-Gama, Wil M.P. van der Aalst, Marcos Sepulveda, Recomposing
 * conformance: Closing the circle on decomposed alignment-based conformance checking in process mining, Information
 * Sciences 466:55-91, Elsevier, 2018. https://doi.org/10.1016/j.ins.2018.07.026
 */
class RangeFitness(
    val aligner: DecompositionAligner,
    val timeout: Long,
    val unit: TimeUnit,
    val eventsSummarizer: EventsSummarizer<Any> = DefaultEventsSummarizer,
    val maxCacheSize: Int = 65535
) : Measure<Log, ClosedFloatingPointRange<Double>> {

    companion object {
        private val emptyTrace = Trace(emptySequence())
    }

    internal val movem: Double by lazy {
        val ecs = ExecutorCompletionService<Double>(aligner.pool)
        val futures = listOf(
            ecs.submit {
                val lb = aligner.alignmentCostLowerBound(emptyList(), timeout, unit)
                if (lb.exact)
                    return@submit lb.cost
                else
                    return@submit Double.NaN
            },
            ecs.submit {
                return@submit aligner.align(emptyTrace).cost.toDouble()
            },
            ecs.submit {
                return@submit AStar(aligner.model).align(emptyTrace).cost.toDouble() // DecompositionAligner seems to fare poorly with empty traces, whereas AStar seems to work just fine
            }
        )
        try {
            while (true) {
                val v = ecs.take().get()
                if (!v.isNaN())
                    return@lazy v
            }
        } finally {
            for (f in futures)
                f.cancel(true)
        }
        error("Cannot compute movem")
    }

    override fun invoke(artifact: Log) = invoke(artifact, null)

    private fun computeEmptyModelAlignmentCost(log: Log) = log.traces.map { trace ->
        trace.events.count() * aligner.penalty.logMove
    }.toList()

    private val cache = object : LinkedHashMap<Any, DecompositionAligner.CostApproximation>() {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Any, DecompositionAligner.CostApproximation>?): Boolean =
            size > maxCacheSize
    }

    private fun alignmentCost(trace: Trace, alignment: Alignment?): DecompositionAligner.CostApproximation {
        if (alignment != null) {
            val c = alignment.cost.toDouble()
            return DecompositionAligner.CostApproximation(c, true)
        }
        val events = trace.events.asCollection()
        val summary = eventsSummarizer(events)
        return cache.computeIfAbsent(summary) {
            aligner.alignmentCostLowerBound(events, timeout, unit)
        }
    }

    /**
     * For each trace in [log], if there is a non-null alignment in the corresponding position of [alignments],
     * use this alignment to compute the cost. Otherwise, use an approximation by [DecompositionAligner].
     */
    operator fun invoke(log: Log, alignments: List<Alignment?>?): ClosedFloatingPointRange<Double> {
        val emptyModelAlignmentCost = computeEmptyModelAlignmentCost(log)
        val movel = emptyModelAlignmentCost.sum()
        var fcostLB = 0.0
        var fcostUB = 0.0
        for ((i, trace) in log.traces.withIndex()) {
            val alignment = if (alignments != null && i < alignments.size) alignments[i] else null
            val costApproximation = alignmentCost(trace, alignment)
            fcostLB += costApproximation.cost
            fcostUB += if (costApproximation.exact)
                costApproximation.cost
            else
                (emptyModelAlignmentCost[i] + movem)
        }
        val fintessLB = 1.0 - fcostUB / (movel + emptyModelAlignmentCost.size * movem)
        val fitnessUB = 1.0 - fcostLB / (movel + emptyModelAlignmentCost.size * movem)
        return fintessLB..fitnessUB
    }

}
