package processm.tools.generator

import java.time.Instant
import java.time.temporal.ChronoUnit

/**
 * A clock that goes faster than the real world. How much faster is determined by [multiplier], and the resulting time
 * is given by [start] + (now-[start])*[multiplier]
 *
 * @param multiplier The default value of 3600 corresponds to 1 hour per 1 second of wall time.
 * You can modify it on the fly, but keep in mind that the resulting timeline will be strange.
 */
class VirtualClock(var multiplier: Long = 3600L, val start: Instant = Instant.now()) {
    operator fun invoke(): Instant {
        val msSinceStart = start.until(Instant.now(), ChronoUnit.MILLIS)
        return start.plus(msSinceStart * multiplier, ChronoUnit.MILLIS)
    }
}