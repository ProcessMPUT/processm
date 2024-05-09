package processm.helpers

import java.time.Duration

private const val SECONDS_IN_DAY = 24.0 * 3600.0
private const val NANOSECONDS_IN_DAY = SECONDS_IN_DAY * 1.0E9

/**
 * The total number of days represented by this Duration, including the fractional part.
 */
val Duration.totalDays: Double
    get() = seconds / SECONDS_IN_DAY + nano / NANOSECONDS_IN_DAY
