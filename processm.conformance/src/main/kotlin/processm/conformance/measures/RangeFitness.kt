package processm.conformance.measures

import processm.conformance.models.alignments.AStar
import processm.conformance.models.alignments.Alignment
import processm.conformance.models.alignments.events.DefaultEventsSummarizer
import processm.conformance.models.alignments.events.EventsSummarizer
import processm.conformance.models.alignments.petrinet.DecompositionAligner
import processm.core.log.hierarchical.Log
import processm.core.log.hierarchical.Trace
import java.util.concurrent.TimeUnit

class RangeFitness(
    val aligner: DecompositionAligner,
    val timeout: Long,
    val unit: TimeUnit,
    val eventsSummarizer: EventsSummarizer<out Any> = DefaultEventsSummarizer(),
    val maxCacheSize: Int = 65535
) : Measure<Log, ClosedFloatingPointRange<Double>> {

    companion object {
        private val emptyTrace = Trace(emptySequence())
    }

    internal val movem: Double by lazy {
        val approximation = aligner.alignmentCostLowerBound(emptyList(), timeout, unit)
        if (approximation.exact)
            return@lazy approximation.cost
        else
            return@lazy AStar(aligner.model).align(emptyTrace).cost.toDouble() // DecompositionAligner seems to fare poorly with empty traces, whereas AStar seems to work just fine
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
        val events = trace.events.toList()
        val summary = eventsSummarizer(events)
        return cache.computeIfAbsent(summary) {
            aligner.alignmentCostLowerBound(events, timeout, unit)
        }
    }

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
                (emptyModelAlignmentCost[i] + movem).toDouble()
        }
        val fintessLB = 1.0 - fcostUB / (movel + emptyModelAlignmentCost.size * movem)
        val fitnessUB = 1.0 - fcostLB / (movel + emptyModelAlignmentCost.size * movem)
        return fintessLB..fitnessUB
    }

}