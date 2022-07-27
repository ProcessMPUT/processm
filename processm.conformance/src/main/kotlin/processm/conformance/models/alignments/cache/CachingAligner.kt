package processm.conformance.models.alignments.cache

import processm.conformance.models.alignments.Aligner
import processm.conformance.models.alignments.Alignment
import processm.conformance.models.alignments.PenaltyFunction
import processm.core.log.hierarchical.Trace
import processm.core.models.commons.ProcessModel

/**
 * A wrapper for [baseAligner] using cache provided by [alignmentCache]
 */
class CachingAligner(val baseAligner: Aligner, val alignmentCache: AlignmentCache) : Aligner {
    companion object {
        private val INVALID = Alignment(emptyList(), -1)
    }

    override val model: ProcessModel
        get() = baseAligner.model

    override val penalty: PenaltyFunction
        get() = baseAligner.penalty

    override fun align(trace: Trace, costUpperBound: Int): Alignment? {
        val events = trace.events.toList()
        val cached = alignmentCache.get(baseAligner.model, events)
        if (cached === INVALID)
            return null
        return cached ?: (baseAligner.align(trace, costUpperBound)
            .also { alignmentCache.put(baseAligner.model, events, it ?: INVALID) })
    }

}
