package processm.conformance.models.alignments

import processm.core.models.commons.ProcessModel
import java.util.concurrent.ExecutorService

/**
 * An interface for the factory of [Aligner]s.
 */
fun interface AlignerFactory {
    /**
     * Produces an [Aligner].
     * @param model The process model to produce [Aligner] for.
     * @param penalty The [PenaltyFunction] that configures the returned [Aligner].
     * @param pool The thread pool that the returned [Aligner] may optionally use.
     * @return The [Aligner] configured with these parameters.
     */
    operator fun invoke(
        model: ProcessModel,
        penalty: PenaltyFunction,
        pool: ExecutorService
    ): Aligner
}
