package processm.core.models.metadata

import processm.core.helpers.stats.AbstractDistribution
import processm.core.helpers.stats.Distribution
import java.time.Duration


/**
 * An [AbstractDistribution] suitable to handle [Duration]s
 */
data class DurationDistributionMetadata private constructor(
    val raw: Distribution
) : MetadataValue, AbstractDistribution<Duration, Duration> {
    companion object {
        private fun Duration.toDouble() = this.toMillis().toDouble()
        private fun Double.toDuration() = Duration.ofMillis(this.toLong())
    }

    constructor(durations: Collection<Duration>) : this(Distribution(durations.map { it.toDouble() }))

    override val min: Duration
        get() = raw.min.toDuration()
    override val Q1: Duration
        get() = raw.Q1.toDuration()
    override val median: Duration
        get() = raw.median.toDuration()
    override val Q3: Duration
        get() = raw.Q3.toDuration()
    override val max: Duration
        get() = raw.max.toDuration()
    override val average: Duration
        get() = raw.average.toDuration()
    override val standardDeviation: Duration
        get() = raw.standardDeviation.toDuration()
    override val count: Int
        get() = raw.count

    override fun quantile(p: Double): Duration = raw.quantile(p).toDuration()

    override fun cdf(v: Duration): Double = raw.cdf(v.toDouble())

    override fun ccdf(v: Duration): Double = raw.ccdf(v.toDouble())

    override fun toString(): String =
        "min: $min; Q1: $Q1; median: $median; Q3: $Q3; max: $max; avg: $average; stddev: $standardDeviation; count: ${raw.raw.size}"
}