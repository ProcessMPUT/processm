package processm.enhancement.simulation

import processm.core.helpers.map2d.DoublingMap2D
import processm.core.helpers.map2d.Map2D
import processm.core.models.commons.*
import kotlin.random.Random

/**
 * Generates business process execution traces.
 * @param processModel the process model to generate traces for.
 * @param activityTransitionsProbabilityWeights for every pair of activities, it defines a weight
 * which is proportional to the probability that the transition between activities occurs.
 */
class Simulation(
    private val processModel: ProcessModel,
    private val activityTransitionsProbabilityWeights: Map2D<String, String, Double> = DoublingMap2D()) {
    private val random = Random.Default
    private val processModelInstance = processModel.createInstance()

    /**
     * Produces sequences of activity instances belonging to the same trace.
     */
    fun generateTraces() = sequence {
        while (true) {
            // Contains (stored as keys) currently available activities. For every available activity,
            // it contains preceding activity which execution caused the activity to be available.
            // Once Issue#145 is implemented, the collection is no longer necessary.
            var precedingActivities = emptyMap<Activity, ActivityInstance?>()

            with(processModelInstance) {
                // setting the state to 'null' restarts the model instance
                setState(null)
                val trace = mutableListOf<ActivityInstance>()
                var lastExecutedActivityInstance: ActivityInstance? = null

                while (!isFinalState) {
                    val activity = run nextActivity@ {
                        val possibleActivities = availableActivities.toList()

                        precedingActivities = possibleActivities.associateWith { activity ->
                            (precedingActivities[activity] ?: lastExecutedActivityInstance)
                        }

                        // the current implementation assumes that each activity only depends on a single preceding activity
                        // generally, the assumption is incorrect and may lead to incorrect results:
                        // an activity could be scheduled to run before all the activities it actually depends on are completed

                        if (possibleActivities.size == 1) {
                            // special case, there is only one successive activity to be executed
                            return@nextActivity possibleActivities.first()
                        } else {

                            if (activityTransitionsProbabilityWeights.rows.any()) {
                                // check if a transition probability is specified for the available next activities
                                // if no, then check the last but one executed activity
                                trace.reversed().forEach { (activityInTrace, _) ->

                                    val succeedingActivitiesWeights = possibleActivities.fold(mutableMapOf<Activity, Double>()) { result, activity ->
                                        activityTransitionsProbabilityWeights.getRow(activityInTrace.name)[activity.name]?.let { weight ->
                                            result[activity] = weight
                                        }

                                        return@fold result
                                    }

                                    val activitiesWeightsSum = succeedingActivitiesWeights.values.sum()
                                    var randomValue = random.nextDouble( activitiesWeightsSum)

                                    succeedingActivitiesWeights.forEach { (succeedingActivity, activityWeight) ->
                                        randomValue -= activityWeight
                                        if (randomValue < 0) {
                                            return@nextActivity succeedingActivity
                                        }
                                    }
                                }
                            }

                            // no appropriate probability found for the current state, so an activity to be executed is selected at random from uniform distribution
                            return@nextActivity possibleActivities[random.nextInt(possibleActivities.size)]
                        }
                    }

                    getExecutionFor(activity).execute()

                    if (!activity.isSilent) {
                        lastExecutedActivityInstance = ActivityInstance(activity, precedingActivities[activity])
                        trace.add(lastExecutedActivityInstance)
                    }
                }

                yield(trace.toList())
            }
        }
    }
}