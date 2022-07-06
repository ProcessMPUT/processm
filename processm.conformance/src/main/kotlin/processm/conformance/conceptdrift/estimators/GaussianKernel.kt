package processm.conformance.conceptdrift.estimators

import org.apache.commons.math3.distribution.NormalDistribution

/**
 * A kernel based on the normal distribution limited to the range of -3..3 (i.e., 3 standard deviations from the mean in both directions)
 */
object GaussianKernel : Kernel {
    private val nd = NormalDistribution(0.0, 1.0)

    override fun invoke(x: Double): Double = nd.density(x)

    //https://en.wikipedia.org/wiki/Normal_distribution#Symmetries_and_derivatives
    override fun derivative(x: Double): Double = -x * this(x)
    override val lowerBound: Double = -3.0
    override val upperBound: Double= 3.0
}