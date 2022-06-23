package processm.conformance.conceptdrift.estimators

interface Kernel : ContinuousDistribution {
    /**
     * Returns the value of probability density function for x
     */
    operator fun invoke(x: Double): Double

    /**
     * Returns the value of the derivative of probability density function for x
     */
    fun derivative(x: Double): Double

    override fun pdf(x: Double): Double = this(x)

    override val relevantRanges: List<ClosedFloatingPointRange<Double>>
        get() = listOf(lowerBound..upperBound)
}