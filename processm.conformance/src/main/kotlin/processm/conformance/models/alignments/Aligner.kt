package processm.conformance.models.alignments

import processm.core.log.hierarchical.Trace

interface Aligner {
    fun align(trace: Trace): Alignment
}
