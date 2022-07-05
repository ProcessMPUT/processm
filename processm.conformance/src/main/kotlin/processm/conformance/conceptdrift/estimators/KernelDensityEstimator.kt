package processm.conformance.conceptdrift.estimators

import processm.conformance.conceptdrift.BucketingDoubleList
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
) : ContinuousDistribution {


    companion object {
        /**
         * A minimal number of points for the KDF to work reasonably well.
         * This value was chosen experimentally.
         */
        const val MIN_N = 4

        private val logger = logger()
    }

    private val points = BucketingDoubleList()

    /**
     * The total number of points used by the estimator
     */
    val n: Int
        get() = points.totalSize
    private val min: Double
        get() = points.first().value
    private val max: Double
        get() = points.last().value

    /** sum of [points] */
    private var s1: Double = 0.0

    /** sum of squares of [points] divided by n-1 */
    private var s2divn1: Double = 0.0

    /**
     * The bandwidth for the estimation (frequently denoted by h).
     */
    var bandwidth: Double = Double.NaN
        private set


    override var lowerBound: Double = Double.NaN
        private set
    override var upperBound: Double = Double.NaN
        private set

    override lateinit var relevantRanges: List<ClosedFloatingPointRange<Double>>
        private set

    /**
     * An unbiased estimation of the mean
     */
    val mean: Double
        get() = s1 / n

    /**
     * A biased estimation of the standard deviation
     *
     * @see Distribution.standardDeviation
     */
    val standardDeviation: Double
        get() = sqrt(s2divn1 - s1 / n * s1 / (n - 1))

    private fun updateBounds() {
        relevantRanges = points.relevantRanges(
            (bandwidth * kernel.lowerBound).absoluteValue,
            (bandwidth * kernel.upperBound).absoluteValue
        )
        lowerBound = relevantRanges.first().start
        upperBound = relevantRanges.last().endInclusive
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
        val ed = Distribution(points.flatten())
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
                if (i != j)
                    r += points[j].counter * kernel((x - points[j].value) / h)
            val n1 = if (i !== null) n - 1 else n
            return r / (n1 * h)
        }

        /**
         * A derivative of the pdf with the bandwidth as the parameter [h] and an option to skip [i]-th data point
         */
        fun fprim(x: Double, h: Double, i: Int?): Double {
            var r = 0.0
            for (j in points.indices)
                if (i != j) {
                    val dx = x - points[j].value
                    r += points[j].counter * (kernel.derivative(dx / h) * dx)
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
            val right = 2.0 * points.withIndex().sumOf { (i, x) -> x.counter * fprim(x.value, h, i) } / n
            return@optimizer left - right
        }.also { logger.trace { "Optimization steps: $ctr" } }
    }

    /**
     * Include [data] in the estimation, updating [mean], [standardDeviation], [bandwidth] and [relevantRanges] in the process
     */
    fun fit(data: Iterable<Double>) {
        var s2 = 0.0
        val oldn = n
        val finite = data.filter(Double::isFinite)
        points.addAll(finite)
        for (x in finite) {
            s1 += x
            s2 += x * x
        }
        if (n > 1) {
            if (oldn > 1)
                s2divn1 *= (oldn - 1).toDouble() / (n - 1)
            s2divn1 += s2 / (n - 1)
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
     *
     * PDF can be computed exactly using the following code:
     * ```
     * override fun pdf(x: Double): Double = points.sumOf { xi -> kernel((x - xi) / bandwidth) } / (n * bandwidth)
     * ```
     */
    override fun pdf(x: Double): Double {
        val lb = points.insertionIndexOf(x - bandwidth * kernel.upperBound)
        val nlb = points.countUpToExclusive(lb)

        val ub = points.insertionIndexOf(x - bandwidth * kernel.lowerBound, lb)
        val nub = points.countFrom(ub)
        return ((lb until ub).sumOf { xi -> points[xi].counter * kernel((x - points[xi].value) / bandwidth) } +
                nlb * kernel(kernel.lowerBound) +
                nub * kernel(kernel.upperBound)) / (n * bandwidth)
    }
}

/**
 * Create a [KernelDensityEstimator] from the given list of values
 */
fun List<Double>.computeKernelDensityEstimator(): KernelDensityEstimator {
    val kdf = KernelDensityEstimator()
    kdf.fit(this.filter(Double::isFinite))
    return kdf
}