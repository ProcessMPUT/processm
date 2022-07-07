package processm.core.helpers.stats

/**
 * A [Distribution] with the support of arbitrary type [Raw].
 */
interface AbstractDistribution<Raw, Aggregate> : DescriptiveStatistics<Raw, Aggregate> {

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