package processm.conformance.models.antialignments

import processm.conformance.models.alignments.Alignment

/**
 * An anti-alignment is an alignment with the maximum cost.
 * See B.F. van Dongen et al., A Unified Approach for Measuring Precision and Generalization Based on Anti-Alignments.
 */
typealias AntiAlignment = Alignment

/**
 * The total number of non-artificial model-only moves
 */
val AntiAlignment.size: Int
    get() = steps.count { it.modelMove !== null && !it.modelMove.isArtificial }
