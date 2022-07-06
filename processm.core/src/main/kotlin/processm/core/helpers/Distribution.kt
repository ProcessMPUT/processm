package processm.core.helpers

import kotlinx.serialization.Serializable
import processm.core.helpers.AbstractDistribution
import kotlin.math.ceil
import kotlin.math.sqrt

/**
 * An empirical distribution based on the given data points.
 *
 * @property raw The raw data points (sorted ascending).
 */
@Serializable
data class Distribution private constructor(
    val raw: DoubleArray
) : AbstractDistribution<Double, Double> {
    companion object {
        private const val ONE_THIRD = 1.0 / 3.0
    }

    constructor(raw: Collection<Double>) : this(raw.toDoubleArray().apply(DoubleArray::sort), null)
    constructor(raw: DoubleArray, dummy: Any? = null) : this(raw.sortedArray())

    init {
        require(raw.isNotEmpty()) { "Empty array of data points is not allowed." }
    }

    /**
     * Minimum (Q0).
     */
    override val min: Double
        get() = raw.first()

    /**
     * First quantile (Q1).
     */
    override val Q1: Double = quantile(0.25)

    /**
     * The median (Q2)
     */
    override val median: Double = quantile(0.5)

    /**
     * Third quantile (Q3).
     */
    override val Q3: Double = quantile(0.75)

    /**
     * Maximum (Q4)
     */
    override val max: Double
        get() = raw.last()

    /**
     * The average of the distribution.
     */
    override val average: Double by lazy(LazyThreadSafetyMode.NONE) { raw.average() }

    /**
     * The corrected estimator of standard deviation of the distribution. This estimator is still biased.
     * See https://en.wikipedia.org/wiki/Standard_deviation#Corrected_sample_standard_deviation
     * and https://en.wikipedia.org/wiki/Standard_deviation#Rapid_calculation_methods
     */
    override val standardDeviation by lazy(LazyThreadSafetyMode.NONE) {
        if (raw.size <= 1)
            return@lazy 0.0

        var s1 = 0.0 // the first raw moment (mean)
        var s2 = 0.0 // the second raw moment
        for (v in raw) {
            s1 += v
            s2 += v * v
        }
        sqrt(s2 / (raw.size - 1) - s1 / raw.size * s1 / (raw.size - 1))
    }

    override val count: Int
        get() = raw.size

    /**
     * Calculates the [p]th quantile of the dataset using the R-8 method as described in
     * https://en.wikipedia.org/wiki/Quantile#Estimating_quantiles_from_a_sample
     * The R-8 method is recommended according to Hyndman, Fan, Sample Quantiles in Statistical Packages,
     * American Statistician. American Statistical Association. 50 (4): 361–365. doi:10.2307/2684934.
     * Quantiles beyond the minimum and maximum in the sample are calculated as minimum and maximum, respectively.
     */
    override fun quantile(p: Double): Double {
        require(p in 0.0..1.0) { "p must be in the range [0; 1], $p given." }
        val N = raw.size
        val h = (N + ONE_THIRD) * p + ONE_THIRD - 1.0 // -1 due to 0-based indexing of [raw]
        val hFloor = h.toInt().coerceAtLeast(0)
        val hCeil = ceil(h).toInt().coerceAtMost(N - 1)
        return raw[hFloor] + (h - hFloor) * (raw[hCeil] - raw[hFloor]) // Qp
    }

    /**
     * Calculates the unbiased estimator of the cumulative distribution function for data point [v].
     * See https://en.wikipedia.org/wiki/Empirical_distribution_function#Definition
     */
    override fun cdf(v: Double): Double {
        val hRaw = raw.deterministicBinarySearch(v, true)
        val h = if (hRaw >= 0) hRaw + 1 else -hRaw - 1
        return h / raw.size.toDouble()
    }

    /**
     * Calculates the unbiased estimator of the complementary cumulative distribution function for data point [v].
     * When [v] is an actual data point, [cdf] and [ccdf] do not sum up to 1, since [v] is included in both ranges.
     */
    override fun ccdf(v: Double): Double {
        val hRaw = raw.deterministicBinarySearch(v, false)
        val h = if (hRaw >= 0) hRaw else -hRaw - 1
        return (raw.size - h) / raw.size.toDouble()
    }

    /**
     * Calculates service level for threshold [t]: the fraction of data points that are smaller or equal to [t].
     */
    fun serviceLevelAtMost(t: Double): Double = cdf(t)

    /**
     * Calculates service level for threshold [t]: the fraction of data points that are greater or equal to [t].
     */
    fun serviceLevelAtLeast(t: Double): Double = ccdf(t)

    override fun toString(): String =
        "min: $min; Q1: $Q1; median: $median; Q3: $Q3; max: $max; avg: $average; stddev: $standardDeviation; count: $count"

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Distribution) return false

        if (!raw.contentEquals(other.raw)) return false

        return true
    }

    override fun hashCode(): Int {
        return raw.contentHashCode()
    }

    private fun DoubleArray.deterministicBinarySearch(element: Double, largest: Boolean = true): Int {
        var i = binarySearch(element)
        if (i < 0)
            return i

        val shift = if (largest) 1 else -1
        while (i + shift in indices && this[i + shift] == element)
            i += shift
        return i
    }
}