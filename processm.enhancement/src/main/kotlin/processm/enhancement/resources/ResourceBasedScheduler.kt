package processm.enhancement.resources

import org.apache.commons.math3.distribution.RealDistribution
import processm.core.models.commons.Activity
import processm.enhancement.simulation.ActivityInstance
import processm.enhancement.simulation.Simulation
import java.time.Duration
import java.time.Instant

class ResourceBasedScheduler(
    private val simulation: Simulation,
    //    private val roleAssignments: Map<Resource, Set<String>>,
    private val resources: List<Resource>,
    private val allowedRoles: Map<Activity, Set<String>>,
    private val activityDurations: Map<Activity, RealDistribution>,
    private val processInstanceStartTime: RealDistribution,
    private val simulationStart: Instant? = null) {

    fun scheduleWith() = sequence {
        var lastProcessStartTime = simulationStart ?: Instant.now()

        simulation.runSingleTrace().forEach { trace ->
            try {
                lastProcessStartTime += Duration.ofMinutes(processInstanceStartTime.sample().toLong())
                val scheduling = mutableMapOf<ActivityInstance, Pair<Instant, Instant>>()

                trace.forEach { activityInstance ->
                    val latestPrecedingActivityEnd = scheduling[activityInstance.executeAfter]?.second
                    val permittedRoles = allowedRoles[activityInstance.activity]
                    val activityDuration =
                        Duration.ofMinutes(activityDurations[activityInstance.activity]!!.sample().toLong())
                    val availableResource = resources.filter { resource ->
                            permittedRoles == null || resource.roles.any {
                                permittedRoles.contains(it)
                            }
                        }.minByOrNull { resource ->
                            resource.getNearestAvailability(
                                activityDuration,
                                latestPrecedingActivityEnd ?: lastProcessStartTime
                            )
                        } ?: throw NoSuchElementException("Noo resources available")
                    val activityStart = availableResource.queueActivity(activityDuration, latestPrecedingActivityEnd ?: lastProcessStartTime)
                    scheduling[activityInstance] = activityStart to activityStart + activityDuration
                }

                yield(scheduling as Map<ActivityInstance, Pair<Instant, Instant>>)
            }
            catch (e: Exception) {

            }
        }
    }
}