package processm.conformance.rca

import processm.conformance.models.alignments.Alignment

/**
 * The result of a ternary classification.
 */
enum class MatchResult {
    MATCH,
    NO_MATCH,
    IRRELEVANT
}

/**
 * A criterion to classify [Alignment]s into three bins defined by [MatchResult]
 */
fun interface Criterion {
    operator fun invoke(alignment: Alignment): MatchResult

    /**
     * Drop [MatchResult.IRRELEVANT] alignments, put the matching ones into [RootCauseAnalysisDataSet.positive] and the non-matching into [RootCauseAnalysisDataSet.negative]
     */
    fun partition(alignments: List<Alignment>): RootCauseAnalysisDataSet {
        val positive = ArrayList<Alignment>()
        val negative = ArrayList<Alignment>()
        for (alignment in alignments) {
            when (this(alignment)) {
                MatchResult.MATCH -> positive.add(alignment)
                MatchResult.NO_MATCH -> negative.add(alignment)
                else -> continue //Intentionally left blank
            }
        }
        return RootCauseAnalysisDataSet(positive, negative)
    }
}