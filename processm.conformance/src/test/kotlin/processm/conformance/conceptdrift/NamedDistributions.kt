package processm.conformance.conceptdrift

import org.apache.commons.math3.distribution.NormalDistribution
import processm.conformance.conceptdrift.estimators.ContinuousDistribution

class MyNormalDistribution(val mean: Double, val stdev: Double) : ContinuousDistribution {

    private val nd = NormalDistribution(mean, stdev)

    override val relevantRanges: List<ClosedFloatingPointRange<Double>> = listOf(mean - 3 * stdev..mean + 3 * stdev)

    override fun pdf(x: Double): Double = nd.density(x)
}

class UniformDistribution(override val lowerBound: Double, override val upperBound: Double) :
    ContinuousDistribution {
    override fun pdf(x: Double): Double = 1.0 / (upperBound - lowerBound)
    override val relevantRanges: List<ClosedFloatingPointRange<Double>> = listOf(lowerBound..upperBound)
}