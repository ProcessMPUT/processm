package processm.conformance.conceptdrift.estimators

interface ContinuousDistribution {

    /**
     * Returns the lowest value for x, such that `this(x)` yields non-negligible probability density
     */
    val lowerBound: Double
        get() = relevantRanges.first().start

    /**
     * Returns the highest value for x, such that `this(x)` yields non-negligible probability density
     */
    val upperBound: Double
        get() = relevantRanges.last().endInclusive

    /**
     * Returns an ordered list of disjoint ranges where the distributions attains non-negligible probability density.
     * The start of the first range is equal to [lowerBound] and the end of the last range is equal to [upperBound].
     * Useful for multimodal distributions, e.g, those generated with [KernelDensityEstimator]
     */
    val relevantRanges: List<ClosedFloatingPointRange<Double>>

    fun pdf(x: Double): Double
}