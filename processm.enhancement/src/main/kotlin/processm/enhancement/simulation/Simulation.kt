package processm.enhancement.simulation

import processm.core.models.commons.Activity
import processm.core.models.commons.ProcessModelInstance
import kotlin.random.Random

/**
 * Generates business process execution traces.
 * @param processModelInstance the process model to generate traces for.
 * @param nextActivityCumulativeDistributionFunction for every activity meant as a current state,
 * it contains a single CDF (Cumulative distribution function) for every set of alternative successive activities.
 * If an activity is not present in the collection, then the next activity is chosen at random from uniform distribution
 */
class Simulation(
    private val processModelInstance: ProcessModelInstance,
    private val nextActivityCumulativeDistributionFunction: Map<Activity, Set<Map<Activity, Double>>> = emptyMap()) {
    private val random = Random.Default

    /**
     * Produces sequences of activity instances belonging to the same trace.
     */
    fun generateTraces() = sequence {
        while (true) {
            // Contains (stored as keys) currently available activities. For every available activity,
            // it contains predecessing activity which execution caused the activity to be available.
            // Once Issue#145 is implemented, the collection is no longer necessary.
            var predecessingActivities = emptyMap<Activity, ActivityInstance?>()

            with(processModelInstance) {
                // setting the state to 'null' restarts the model instance
                setState(null)
                val trace = mutableListOf<ActivityInstance>()
                var lastExecutedActivityInstance: ActivityInstance? = null

                while (!isFinalState) {
                    run nextActivity@ {
                        val possibleActivities = availableActivities.toList()

                        predecessingActivities = possibleActivities.associateWith { activity ->
                            (predecessingActivities[activity] ?: lastExecutedActivityInstance)
                        }

                        // the current implementation assumes that each activity only depends on a single other activity
                        // generally, the assumption is incorrect and may lead to incorrect results:
                        // an activity could be scheduled to run before all the activities it actually depends on are completed

                        if (possibleActivities.size == 1) {
                            // special case, there is only one successive activity to be executed
                            val activity = possibleActivities.first()
                            getExecutionFor(activity).execute()
                            lastExecutedActivityInstance = ActivityInstance(activity, predecessingActivities[activity])
                            trace.add(lastExecutedActivityInstance!!)
                        } else {
                            // check if the last executed activity has appropriate CDF specified for the available next activities
                            // if no CDF is defined, then check the last but one executed activity
                            trace.reversed().forEach { (traceActivity, _) ->
                                nextActivityCumulativeDistributionFunction[traceActivity]?.forEach { successingActivitiesCdf ->
                                    if (possibleActivities.containsAll(successingActivitiesCdf.keys)) {
                                        val randomValue = random.nextDouble() // randomValue in in <0;1)
                                        val nextActivity = successingActivitiesCdf
                                            .filterValues { nextActivityCdf -> nextActivityCdf > randomValue }
                                            .minByOrNull { it.value }
                                            ?.key

                                        if (nextActivity != null) {
                                            getExecutionFor(nextActivity).execute()
                                            lastExecutedActivityInstance = ActivityInstance(nextActivity, predecessingActivities[nextActivity])
                                            trace.add(lastExecutedActivityInstance!!)
                                            return@nextActivity
                                        }
                                    }
                                }
                            }

                            // no appropriate CDF found for the current state, so the activity to be executed is selected at random from uniform distribution
                            val randomNextActivity = possibleActivities[random.nextInt(possibleActivities.size)]
                            getExecutionFor(randomNextActivity).execute()
                            lastExecutedActivityInstance = ActivityInstance(randomNextActivity, predecessingActivities[randomNextActivity])
                            trace.add(lastExecutedActivityInstance!!)
                        }
                    }
                }

                yield(trace.toList())
            }
        }
    }
}