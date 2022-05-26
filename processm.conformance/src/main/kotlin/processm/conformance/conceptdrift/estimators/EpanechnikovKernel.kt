package processm.conformance.conceptdrift.estimators

import kotlin.math.absoluteValue
import kotlin.math.pow

/**
 * Optimal w.r.t. MSE [1], but supposedly the kernel choice doesn't matter much [2]
 *
 * [1] https://doi.org/10.1137%2F1114019
 * [2] https://www.bauer.uh.edu/rsusmel/phd/ec1-26.pdf page 10
 */
object EpanechnikovKernel : Kernel {
    override fun invoke(x: Double): Double = if (x.absoluteValue <= 1) 0.75 * (1 - x.pow(2)) else 0.0

    /**
     * Strictly speaking for x=-1 and x=1 this should be NaN, but I don't think this would be useful
     */
    override fun derivative(x: Double): Double = if (x.absoluteValue <= 1) -0.75 * 2 * x else 0.0
    override val lowerBound: Double= -1.0
    override val upperBound: Double=1.0
}