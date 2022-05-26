package processm.conformance.conceptdrift.estimators

interface Kernel {
    /**
     * Returns the value of probability density function for x
     */
    operator fun invoke(x: Double): Double

    /**
     * Returns the value of the derivative of probability density function for x
     */
    fun derivative(x: Double): Double

    /**
     * Returns the lowest value for x, such that `this(x)` yields non-negligible probability
     */
    val lowerBound: Double

    /**
     * Returns the highest value for x, such that `this(x)` yields non-negligible probability
     */
    val upperBound: Double
}