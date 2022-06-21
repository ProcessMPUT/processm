package processm.enhancement.resources

import java.time.Duration
import java.time.Instant

/**
 * Represents a resource that can execute activities with based on time constraints.
 */
interface Resource {

    /**
     * A collection of roles assigned to the resource.
     */
    val roles: Set<String>

    /**
     * Calculates the nearest availability window for the specified activity.
     * @param activityDuration Duration of the activity.
     * @param after The earliest moment at which the activity can be executed.
     * @return The earliest available scheduling.
     */
    fun getNearestAvailability(activityDuration: Duration, after: Instant): Instant

    /**
     * Enqueues the specified activity for the resource.
     * @param activityDuration Duration of the activity.
     * @param after The earliest moment at which the activity can be executed.
     * @return The scheduling assigned for the activity.
     */
    fun enqueueActivity(activityDuration: Duration, after: Instant): Instant
}