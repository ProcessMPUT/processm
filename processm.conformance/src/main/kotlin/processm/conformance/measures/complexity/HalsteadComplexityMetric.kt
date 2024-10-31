package processm.conformance.measures.complexity

import kotlin.math.log2

/**
 * Represents the Halstead Complexity Metric.
 * Instantiate this class using the [Halstead.invoke] method.
 * See J. Cardoso, J. Mendling2, G. Neumann, and H.A. Reijers, A Discourse on Complexity of Process Models,
 * BPM 2006 Workshops, LNCS 4103, pp. 115–126, 2006
 * and
 * M. H. Halstead. Elements of Software Science. Elsevier, Amsterdam, 1987.
 *
 * @property uniqueOperators The number of unique activities, splits, joins, and control flow elements (n1).
 * @property uniqueOperands The number of unique data variables and activities (n2).
 * @property totalOperators The total number of activities, splits, joins, and control flow elements (N1).
 * @property totalOperands The total number of data variables and activities (N2).
 */
data class HalsteadComplexityMetric internal constructor(
    val uniqueOperators: Int,
    val uniqueOperands: Int,
    val totalOperators: Int,
    val totalOperands: Int
) {
    /**
     * Process length. Derived from Halstead's calculated estimated program length (\hat{N}).
     */
    val length: Double = uniqueOperators * log2(uniqueOperators.coerceAtLeast(1).toDouble()) +
            uniqueOperands * log2(uniqueOperands.coerceAtLeast(1).toDouble())

    /**
     * Process volume. Derived from Halstead's volume (V).
     */
    val volume: Double =
        (totalOperators + totalOperands) * log2((uniqueOperators + uniqueOperands).coerceAtLeast(1).toDouble())

    /**
     * Process difficulty. Derived from Halstead's difficulty (D).
     */
    val difficulty: Double =
        if (totalOperands > 0 && uniqueOperands > 0) (uniqueOperators / 2.0 * totalOperands / uniqueOperands)
        else 0.0

    /**
     * Halstead's effort (E)
     */
    val effort: Double = volume * difficulty
}
