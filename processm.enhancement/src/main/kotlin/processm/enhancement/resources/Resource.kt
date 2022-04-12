package processm.enhancement.resources

import processm.core.models.commons.Activity
import processm.enhancement.simulation.ActivityInstance
import java.time.Instant
import java.time.Duration

interface Resource {
    val roles: Set<String>

    fun getNearestAvailability(activityDuration: Duration, after: Instant): Instant

    fun queueActivity(activityDuration: Duration, after: Instant): Instant
}