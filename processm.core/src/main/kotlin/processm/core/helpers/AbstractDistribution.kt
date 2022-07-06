package processm.core.helpers

interface AbstractDistribution<Raw, Aggregate> {
    /**
     * Minimum (Q0).
     */
    val min: Raw

    /**
     * First quantile (Q1).
     */
    val Q1: Aggregate

    /**
     * The median (Q2)
     */
    val median: Aggregate

    /**
     * Third quantile (Q3).
     */
    val Q3: Aggregate

    /**
     * Maximum (Q4)
     */
    val max: Raw

    /**
     * The average of the distribution.
     */
    val average: Aggregate

    /**
     * An estimation of standard deviation of the distribution.
     */
    val standardDeviation: Aggregate

    /**
     * Number of data points supporting the distribution
     */
    val count: Int

    /**
     * The [p]th quantile of the dataset
     */
    fun quantile(p: Double): Aggregate

    /**
     * An unbiased estimation of the cumulative distribution function for data point [v].
     */
    fun cdf(v: Aggregate): Double

    /**
     * An unbiased estimation of the complementary cumulative distribution function for data point [v].
     * When [v] is an actual data point, [cdf] and [ccdf] do not sum up to 1, since [v] is included in both ranges.
     */
    fun ccdf(v: Aggregate): Double
}