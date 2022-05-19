package processm.conformance.rca

import processm.conformance.models.alignments.Alignment

/**
 * The result of a ternary classification.
 */
enum class MatchResult {
    /**
     * Denotes that a case matches the given criterion, e.g., for [ActivityEventCriterion] the given alignment contains misalignment for the given pair (activity, event)
     */
    MATCH,

    /**
     * Denotes that a case does not match the given criterion, but it could have matched it, because it contains relevant information.
     *
     * E.g., for [ActivityEventCriterion] the given alignment does not contain misalignment for the given pair (activity, event), but contains an aligned pair.
     */
    NO_MATCH,

    /**
     * Denotes that it is meaningless to consider whether a case matches or not, e.g., because it does not contain relevant information.
     *
     * E.g., for [ActivityEventCriterion] the given alignment does not contain any occurrence of the given activity and/or event.
     */
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