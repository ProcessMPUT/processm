package processm.conformance.models.alignments

import processm.core.log.hierarchical.Trace

/**
 * An interface for generic model and trace aligner.
 */
interface Aligner {
    /**
     * Calculates [Alignment] for the given [trace].
     *
     * @throws IllegalStateException If the alignment cannot be calculated, e.g., because the final model state is not
     * reachable.
     */
    fun align(trace: Trace): Alignment
}
