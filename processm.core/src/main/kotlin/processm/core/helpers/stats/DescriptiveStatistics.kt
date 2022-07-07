package processm.core.helpers.stats

/**
 * Descriptive statistics for a collection of data points of the type [Raw].
 * [Aggregate] is a datatype to represent aggregated values, such as the average,
 * which are not necessarily of the type [Raw], e.g., when [Raw]=[Int].
 */
interface DescriptiveStatistics<Raw, Aggregate> {
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
}