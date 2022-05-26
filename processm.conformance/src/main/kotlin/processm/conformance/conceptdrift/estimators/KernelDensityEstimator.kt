package processm.conformance.conceptdrift.estimators

import processm.conformance.conceptdrift.numerical.integration.Integrator
import processm.conformance.conceptdrift.numerical.integration.MidpointIntegrator
import processm.conformance.conceptdrift.numerical.optimization.Optimizer
import processm.conformance.conceptdrift.numerical.optimization.RMSProp
import processm.core.helpers.Distribution
import processm.core.logging.logger
import processm.core.logging.trace
import kotlin.math.absoluteValue
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * Available methods to compute the bandwidth in [KernelDensityEstimator]
 */
enum class BandwidthSelectionMethod {
    /**
     * Use the rule of thumb - see https://en.wikipedia.org/wiki/Kernel_density_estimation#A_rule-of-thumb_bandwidth_estimator
     */
    RULE_OF_THUMB,

    /**
     * Use the Silverman's rule of thumb. See https://en.wikipedia.org/wiki/Kernel_density_estimation#A_rule-of-thumb_bandwidth_estimator
     */
    SILVERMAN,

    /**
     * Use least squares cross validation. See https://git.processm.cs.put.poznan.pl/processm-team/processm/-/wikis/KDF
     */
    LEAST_SQUARES_CROSS_VALIDATION
}

/**
 * Kernel density estimation with the given [kernel], e.g., [GaussianKernel] or [EpanechnikovKernel]
 *
 * [org.apache.spark.mllib.stat.KernelDensity] was not used due to a fixed kernel, lack of bandwidth computation and
 * lack of [lowerBound] and [upperBound]
 *
 * [optimizer], [integrator] and [bandwidthFixpointPrecision] are used only if [bandwidthSelectionMethod] is set to
 * [BandwidthSelectionMethod.LEAST_SQUARES_CROSS_VALIDATION]
 */
class KernelDensityEstimator(
    val kernel: Kernel = ApproximateGaussianKernel.KERNEL_2_3,
    val bandwidthSelectionMethod: BandwidthSelectionMethod = BandwidthSelectionMethod.RULE_OF_THUMB,
    var optimizer: Optimizer = RMSProp(),
    var integrator: Integrator = MidpointIntegrator(0.001),
    var bandwidthFixpointPrecision: Double = 1e-3
) : ContinousDistribution {


    companion object {
        /**
         * A minimal number of points for the KDF to work reasonably well.
         * This value was chosen experimentally.
         */
        const val MIN_N = 4

        private val logger = logger()
    }

    private val points = ArrayList<Double>()
    private val n: Int
        get() = points.size
    private val min: Double
        get() = points.first()
    private val max: Double
        get() = points.last()

    /** sum of [points] */
    private var s1: Double = 0.0

    /** sum of squares of [points] */
    private var s2: Double = 0.0

    /**
     * The bandwidth for the estimation (frequently denoted by h).
     */
    var bandwidth: Double = Double.NaN
        private set


    override var lowerBound: Double = Double.NaN
        private set
    override var upperBound: Double = Double.NaN
        private set

    val mean: Double
        get() = s1 / n
    val standardDeviation: Double
        get() = sqrt(s2 / (n - 1) - s1 / n * s1 / (n - 1))

    private fun updateBounds() {
        //(x - min) / bandwidth) = kernel.lowerBound
        lowerBound = kernel.lowerBound * bandwidth + min
        //(x - max) / bandwidth) = kernel.upperBound
        upperBound = kernel.upperBound * bandwidth + max
    }

    /**
     * See https://en.wikipedia.org/wiki/Kernel_density_estimation#A_rule-of-thumb_bandwidth_estimator
     */
    private fun ruleOfThumb(): Double {
        return 1.06 * standardDeviation * (n.toDouble().pow(-1.0 / 5))
    }

    /**
     * See https://en.wikipedia.org/wiki/Kernel_density_estimation#A_rule-of-thumb_bandwidth_estimator
     */
    private fun silverman(): Double {
        val ed = Distribution(points)
        return 0.9 * minOf(standardDeviation, (ed.Q3 - ed.Q1) / 1.34) * (n.toDouble().pow(-1.0 / 5))
    }

    /**
     * See https://git.processm.cs.put.poznan.pl/processm-team/processm/-/wikis/KDF
     */
    private fun leastSquaresCrossValidation(): Double {
        /**
         * This is the pdf, but with the bandwidth as the parameter [h] and an option to skip [i]-th data point
         */
        fun f(x: Double, h: Double, i: Int?): Double {
            var r = 0.0
            for (j in points.indices)
                if (i == null || i != j)
                    r += kernel((x - points[j]) / h)
            val n1 = if (i !== null) n - 1 else n
            return r / (n1 * h)
        }

        /**
         * A derivative of the pdf with the bandiwdth as the parameter [h] and an option to skip [i]-th data point
         */
        fun fprim(x: Double, h: Double, i: Int?): Double {
            var r = 0.0
            for (j in points.indices)
                if (i === null || i != j) {
                    val dx = x - points[j]
                    r += kernel.derivative(dx / h) * dx
                }
            val n1 = if (i !== null) n - 1 else n
            return r / (n1 * h.pow(4))
        }

        var ctr = 0
        return optimizer(if (bandwidth.isFinite()) bandwidth else ruleOfThumb()) { h ->
            ctr++
            val left = integrator(lowerBound, upperBound) { x ->
                2 * f(x, h, null) * fprim(x, h, null)
            }
            val right = 2.0 * points.withIndex().sumOf { (i, x) -> fprim(x, h, i) } / n
            return@optimizer left - right
        }.also { logger.trace { "Optimization steps: $ctr" } }
    }

    fun fit(data: Iterable<Double>) {
        for (x in data) {
            var i = points.binarySearch(x)
            if (i < 0)
                i = -i - 1
            points.add(i, x)
            assert(i == 0 || points[i - 1] <= points[i])
            assert(i == points.size - 1 || points[i] <= points[i + 1])
            s1 += x
            s2 += x * x
        }
        if (points.size >= MIN_N) {
            if (!lowerBound.isFinite() || lowerBound > min || upperBound < max) {
                /*
                It is either the first call to `fit` and we have no idea about the distribution,
                or some of the newly added points are outside of the range as determined earlier.
                Either way, we start from scratch assuming we know nothing about the real distribution and
                we use the Chebyshev's inequality to (over)estimate the bounds
                https://en.wikipedia.org/wiki/Chebyshev%27s_inequality

                Assume we're fine with skipping outermost 0.01 of probability, then:
                1/k^2 = 0.01
                k=sqrt(1/0.01)=10
                */
                if (lowerBound.isFinite()) {
                    logger.trace { "Restarting bounds from scratch. Current range: [$min, $max], previous bounds: [$lowerBound, $upperBound]" }
                }
                val k = 10
                lowerBound = mean - k * standardDeviation
                upperBound = mean + k * standardDeviation
            }
            when (bandwidthSelectionMethod) {
                BandwidthSelectionMethod.RULE_OF_THUMB -> {
                    bandwidth = ruleOfThumb()
                    updateBounds()
                }
                BandwidthSelectionMethod.SILVERMAN -> {
                    bandwidth = silverman()
                    updateBounds()
                }
                BandwidthSelectionMethod.LEAST_SQUARES_CROSS_VALIDATION -> {
                    // compute a fixpoint of three values: bandwidth, lowerBound, upperBound
                    logger.trace { "Starting LSCV" }
                    while (true) {
                        val newBandwidth = leastSquaresCrossValidation()
                        logger.trace { "bandwidth: $bandwidth -> $newBandwidth" }
                        if ((newBandwidth - bandwidth).absoluteValue < bandwidthFixpointPrecision)
                            break
                        bandwidth = newBandwidth
                        updateBounds()
                    }
                }
            }
        }
    }

    /**
     * An approximation of pdf. Ignores points far enough from x to be of little importance.
     */
    override fun pdf(x: Double): Double {
        // The commented out code is a slower variant of the code below it using binarySearch

//        var lb = 0
//        while (lb < points.size && (x - bandwidth * kernel.upperBound) > points[lb])
//            lb++

        val lb = run {
            val xlb = x - bandwidth * kernel.upperBound
            var lb = points.binarySearch(xlb)
            if (lb >= 0)
                while (lb < points.size && xlb > points[lb + 1])
                    lb++
            else
                lb = -lb - 1
            lb
        }

//        var ub = points.size - 1
//        while (ub >= 0 && (x - bandwidth * kernel.lowerBound) < points[ub])
//            ub--

        val ub = run {
            val xub = x - bandwidth * kernel.lowerBound
            var ub = points.binarySearch(xub)
            if (ub >= 0)
                while (ub >= 0 && xub < points[ub - 1])
                    ub--
            else
                ub = -ub - 1
            ub
        }
        return ((lb until ub).sumOf { xi -> kernel((x - points[xi]) / bandwidth) } +
                lb * kernel(kernel.lowerBound) +
                (n - ub) * kernel(kernel.upperBound)) / (n * bandwidth)
    }

    /**
     * This is an exact way to compute pdf. Left here for documentation purposes.
     */
//    override fun pdf(x: Double): Double = points.sumOf { xi -> kernel((x - xi) / bandwidth) } / (n * bandwidth)

}