package processm.enhancement.resources

import org.apache.commons.math3.distribution.RealDistribution
import processm.core.logging.loggedScope
import processm.core.models.commons.Activity
import processm.enhancement.simulation.ActivityInstance
import processm.enhancement.simulation.Simulation
import java.time.Duration
import java.time.Instant

/**
 * A scheduler that assigns timestamps to the activity instances contained in simulated traces based on resource availability.
 */
class ResourceBasedScheduler(
    private val simulation: Simulation,
    private val resources: List<Resource>,
    private val activitiesRoles: Map<Activity, Set<String>>,
    private val activityDurationsDistributions: Map<Activity, RealDistribution>,
    private val processInstanceOccurringRate: RealDistribution,
    private val simulationStartOffset: Instant? = null) {

    /**
     * Produces traces enriched with scheduling details.
     */
    fun scheduleWith() = sequence {
        var lastProcessExecutionStartTime = simulationStartOffset ?: Instant.now()

        simulation.generateTraces().forEach { trace ->
            try {
                lastProcessExecutionStartTime += Duration.ofMinutes(processInstanceOccurringRate.sample().toLong())
                val scheduling = mutableMapOf<ActivityInstance, Pair<Instant, Instant>>()

                trace.forEach { activityInstance ->
                    val latestPrecedingActivityEnd = scheduling[activityInstance.executeAfter]?.second
                    val permittedRoles = activitiesRoles[activityInstance.activity]
                    val activityDuration = Duration.ofMinutes(activityDurationsDistributions[activityInstance.activity]!!.sample().toLong())
                    val availableResource = resources.filter { resource ->
                            permittedRoles == null || resource.roles.any {
                                permittedRoles.contains(it)
                            }
                        }.minByOrNull { resource ->
                            resource.getNearestAvailability(
                                activityDuration,
                                latestPrecedingActivityEnd ?: lastProcessExecutionStartTime
                            )
                        } ?: throw NoSuchElementException("No resources available with required set of privileges to handle activity ${activityInstance.activity.name}")
                    val activityStart = availableResource.enqueueActivity(activityDuration, latestPrecedingActivityEnd ?: lastProcessExecutionStartTime)
                    scheduling[activityInstance] = activityStart to activityStart + activityDuration
                }

                yield(scheduling as Map<ActivityInstance, Pair<Instant, Instant>>)
            }
            catch (e: Exception) {
                loggedScope { logger ->
                    logger.warn("An error occurred while calculating scheduling for simulated trace", e)
                    throw e
                }
            }
        }
    }
}