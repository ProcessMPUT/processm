package processm.conformance.models.alignments.cache

import processm.conformance.models.alignments.Aligner
import processm.conformance.models.alignments.AlignerFactory
import processm.conformance.models.alignments.PenaltyFunction
import processm.core.models.commons.ProcessModel
import java.util.concurrent.ExecutorService

/**
 * An [AlignerFactory] implementing a shared [cache] on top of aligners created by [baseAlignerFactory]
 */
class CachingAlignerFactory(val cache: AlignmentCache?, val baseAlignerFactory: AlignerFactory) : AlignerFactory {
    override fun invoke(model: ProcessModel, penalty: PenaltyFunction, pool: ExecutorService): Aligner {
        val base = baseAlignerFactory(model, penalty, pool)
        return if (cache != null) CachingAligner(base, cache) else base
    }

}