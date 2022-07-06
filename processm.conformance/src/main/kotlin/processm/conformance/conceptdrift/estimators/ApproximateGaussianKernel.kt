package processm.conformance.conceptdrift.estimators

import org.apache.commons.math3.distribution.NormalDistribution
import kotlin.math.pow

/**
 * An approximation of [GaussianKernel] precomputing all the values of the Gaussian probability density function (PDF)
 * for arguments from within the range -[range]..[range] with the step of 10^[log10resolution]
 */
class ApproximateGaussianKernel(private val log10resolution: Int, private val range: Int) : Kernel {

    companion object {
        private val nd = NormalDistribution(0.0, 1.0)

        /**
         * A reasonable default configuration with resolution of 2 decimal places and range of -3..3
         * (c.f. [https://en.wikipedia.org/w/index.php?title=68%E2%80%9395%E2%80%9399.7_rule&oldid=1080869256](https://en.wikipedia.org/w/index.php?title=68%E2%80%9395%E2%80%9399.7_rule&oldid=1080869256))
         */
        val KERNEL_2_3 = ApproximateGaussianKernel(2, 3)
    }

    private val resolution: Double = 10.0.pow(log10resolution)
    private val pdf: DoubleArray = DoubleArray((2 * range * resolution).toInt() + 1) { i ->
        nd.density(i / resolution - range)
    }

    /**
     * Returns the value of the Gaussian PDF for [x] rounded to [log10resolution] decimal places if within -[range]..[range], or `PDF(-range)` otherwise
     */
    override fun invoke(x: Double): Double =
        pdf[if (x < -range || x >= range) 0 else ((x + range) * resolution).toInt()]


    //https://en.wikipedia.org/wiki/Normal_distribution#Symmetries_and_derivatives
    override fun derivative(x: Double): Double = -x * this(x)
    override val lowerBound: Double = -range.toDouble()
    override val upperBound: Double = range.toDouble()
}