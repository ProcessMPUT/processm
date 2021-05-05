package processm.conformance.measures

import processm.conformance.models.alignments.Alignment
import processm.conformance.models.alignments.events.DefaultEventsSummarizer
import processm.conformance.models.alignments.events.EventsSummarizer
import processm.conformance.models.alignments.petrinet.DecompositionAligner
import processm.core.log.hierarchical.Log
import processm.core.log.hierarchical.Trace
import java.util.concurrent.TimeUnit

/**
 * This is an upper bound on [Fitness]
 */
class UBFitness(
    override val aligner: DecompositionAligner,
    val timeout: Long,
    val unit: TimeUnit,
    val eventsSummarizer: EventsSummarizer<out Any> = DefaultEventsSummarizer(),
    val maxCacheSize: Int = 65535
) : Fitness(aligner) {

    private val cache = object : LinkedHashMap<Any, Double>() {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Any, Double>?): Boolean = size > maxCacheSize
    }

    override fun alignmentCost(trace: Trace, alignment: Alignment?): Double? {
        val superResult = super.alignmentCost(trace, alignment)
        if (superResult != null)
            return superResult
        val events = trace.events.toList()
        val summary = eventsSummarizer(events)
        val result = cache.computeIfAbsent(summary) {
            aligner.alignmentCostLowerBound(events, timeout, unit) ?: Double.NaN
        }
        return if (!result.isNaN())
            result
        else
            null
    }

    override fun invoke(log: Log): Double = invoke(log, null)
}