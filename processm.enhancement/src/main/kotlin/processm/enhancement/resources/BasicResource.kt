package processm.enhancement.resources

import java.time.Duration
import java.time.Instant
import java.util.*

/**
 * Implements a resource that keeps track of enqueued activities to make sure no overlapping occurs.
 * @param
 */
class BasicResource(override val roles: Set<String>): Resource {
    // TODO: Consider implementing it as a data structure dedicated for storing intervals (e.g. interval tree, segment tree)
    private val activities = TreeMap<Instant, Instant>()

    override fun getNearestAvailability(activityDuration: Duration, after: Instant): Instant {
        var newActivityStart: Instant? = null

        run breaking@ {
            activities.forEach { (activityStart, activityEnd) ->
                if (activityEnd < after) return@forEach

                if (newActivityStart == null)
                    newActivityStart = activityEnd
                else if (newActivityStart!! + activityDuration < activityStart)
                    return@breaking
                else
                    newActivityStart = activityEnd
            }
        }

        return newActivityStart ?: after
    }

    override fun enqueueActivity(activityDuration: Duration, after: Instant): Instant {
        val nearestAvailability = getNearestAvailability(activityDuration, after)
        activities[nearestAvailability] = nearestAvailability + activityDuration
        return nearestAvailability
    }
}