package processm.conformance.conceptdrift.estimators

interface ContinuousDistribution {

    val lowerBound: Double
        get() = relevantRanges.first().start
    val upperBound: Double
        get() = relevantRanges.last().endInclusive

    val relevantRanges: List<ClosedFloatingPointRange<Double>>

    fun pdf(x: Double): Double
}