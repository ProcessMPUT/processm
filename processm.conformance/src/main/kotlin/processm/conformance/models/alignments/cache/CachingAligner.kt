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

    override val model: ProcessModel
        get() = baseAligner.model

    override val penalty: PenaltyFunction
        get() = baseAligner.penalty

    override fun align(trace: Trace): Alignment {
        val events = trace.events.toList()
        return alignmentCache.get(baseAligner.model, events) ?: (baseAligner.align(trace)
            .also { alignmentCache.put(baseAligner.model, events, it) })
    }

}