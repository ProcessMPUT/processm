package processm.enhancement.resources

import org.apache.commons.math3.distribution.RealDistribution
import processm.core.log.*
import processm.core.log.attribute.Attribute
import processm.core.log.attribute.Attribute.LIFECYCLE_TRANSITION
import processm.core.log.attribute.Attribute.ORG_RESOURCE
import processm.core.log.attribute.Attribute.ORG_ROLE
import processm.core.log.attribute.Attribute.TIME_TIMESTAMP
import processm.enhancement.simulation.CAUSE
import java.time.Duration
import java.time.Instant
import java.util.*

/**
 * A XES stream that applies scheduling to the events in the base XES [stream] based on resource availability.
 * The events in the resulting stream are extended with org:resource, org:role, and time:timestamp attributes.
 *
 * @exception IllegalArgumentException if any event misses the identity:id and/or concept:name attribute.
 */
class ApplyResourceBasedScheduling(
    private val stream: XESInputStream,
    private val resources: List<Resource>,
    private val activitiesRoles: Map<String, Set<String>>,
    private val activityDurationsDistributions: Map<String, RealDistribution>,
    private val processInstanceOccurringRate: RealDistribution,
    private val startOffset: Instant? = null
) : XESInputStream {

    companion object {
        private val ASSIGN = "assign"
        private val START = "start"
        private val COMPLETE =  "complete"
    }

    private val random: kotlin.random.Random = kotlin.random.Random.Default

    override fun iterator(): Iterator<XESComponent> = sequence {
        var lastProcessExecutionStartTime = startOffset ?: Instant.now()!!
        var lastEndEventTime: Instant? = null
        val scheduling = mutableMapOf<UUID, Pair<Instant, Instant>>()
        val eventBuffer = mutableListOf<Event>()

        for (component in stream) {
            when (component) {
                is Event -> {
                    requireNotNull(component.identityId) { "The identity:id attribute must be set for every event." }
                    requireNotNull(component.conceptName) { "The concept:name attribute must be set for every event." }

                    val latestPrecedingActivityEnd =
                        scheduling[component.attributes.getOrNull(Attribute.CAUSE)]?.second
                            ?: lastEndEventTime
                            ?: lastProcessExecutionStartTime
                    val permittedRoles = activitiesRoles[component.conceptName]
                    val duration =
                        Duration.ofMinutes(activityDurationsDistributions[component.conceptName]!!.sample().toLong())
                    val resource = resources
                        .filter { resource ->
                            permittedRoles == null || resource.roles.any { permittedRoles.contains(it) }
                        }.minByOrNull { resource ->
                            resource.getNearestAvailability(duration, latestPrecedingActivityEnd)
                        }
                        ?: throw NoSuchElementException("No resources available with required set of privileges to handle activity ${component.conceptName}")

                    val activityStart = resource.enqueueActivity(duration, latestPrecedingActivityEnd)
                    scheduling[component.identityId!!] = activityStart to activityStart + duration

                    val attributesAssign = MutableAttributeMap(component.attributes)
                    attributesAssign[TIME_TIMESTAMP] = latestPrecedingActivityEnd
                    attributesAssign[LIFECYCLE_TRANSITION] = ASSIGN
                    attributesAssign[ORG_RESOURCE] = resource.name
                    if (permittedRoles !== null) {
                        val role = resource.roles.intersect(permittedRoles).random(random)
                        attributesAssign[ORG_ROLE] = role
                    }

                    val attributesStart = MutableAttributeMap(attributesAssign)
                    attributesStart[TIME_TIMESTAMP] = activityStart
                    attributesStart[LIFECYCLE_TRANSITION] = START
                    val attributesComplete = MutableAttributeMap(attributesStart)
                    attributesComplete[LIFECYCLE_TRANSITION] = COMPLETE
                    attributesComplete[TIME_TIMESTAMP] = activityStart + duration

                    eventBuffer.add(Event(attributesAssign))
                    eventBuffer.add(Event(attributesStart))
                    eventBuffer.add(Event(attributesComplete))

                    lastEndEventTime = activityStart + duration
                }
                is Log, is Trace -> {
                    if (eventBuffer.isNotEmpty()) {
                        eventBuffer.sortBy(Event::timeTimestamp)
                        yieldAll(eventBuffer)
                        eventBuffer.clear()
                    }
                    scheduling.clear()
                    if (component is Trace)
                        lastProcessExecutionStartTime += Duration.ofMinutes(
                            processInstanceOccurringRate.sample().toLong()
                        )
                    lastEndEventTime = null

                    yield(component)
                }
            }
        }

        if (eventBuffer.isNotEmpty()) {
            eventBuffer.sortBy(Event::timeTimestamp)
            yieldAll(eventBuffer)
        }
    }.iterator()
}
