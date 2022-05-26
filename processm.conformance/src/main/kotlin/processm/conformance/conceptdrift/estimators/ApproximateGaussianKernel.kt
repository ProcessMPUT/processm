package processm.conformance.conceptdrift.estimators

import org.apache.commons.math3.distribution.NormalDistribution
import kotlin.math.pow

class ApproximateGaussianKernel(log10resolution: Int, private val range: Int) : Kernel {

    companion object {
        private val nd = NormalDistribution(0.0, 1.0)
        val KERNEL_2_3 = ApproximateGaussianKernel(2, 3)
    }

    private val resolution: Double = 10.0.pow(log10resolution)
    private val pdf: DoubleArray = DoubleArray((2 * range * resolution).toInt() + 1) { i ->
        nd.density(i / resolution - range)
    }

    override fun invoke(x: Double): Double {
        if (x < -range || x >= range)
            return pdf[0]
        val i = ((x + range) * resolution).toInt()
        return (pdf[i] + pdf[i + 1]) / 2
    }


    //https://en.wikipedia.org/wiki/Normal_distribution#Symmetries_and_derivatives
    override fun derivative(x: Double): Double = -x * this(x)
    override val lowerBound: Double = -range.toDouble()
    override val upperBound: Double= range.toDouble()
}