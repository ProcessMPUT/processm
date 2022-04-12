package processm.enhancement.simulation

//import processm.core.models.causalnet.CausalNetNode.clone
import org.apache.commons.math3.distribution.NormalDistribution
import org.apache.commons.math3.distribution.RealDistribution
import processm.core.models.commons.Activity
import processm.core.models.commons.ProcessModel
import processm.core.models.commons.ProcessModelInstance
import java.time.Duration
import java.time.Instant
import kotlin.random.Random

class Simulation(private val processModelInstance: ProcessModelInstance, private val nextActivityCumulativeDistributionFunction: Map<Activity, Set<Map<Activity, Double>>> = emptyMap()) {
    private val random = Random.Default
//    private val normalDistribution = NormalDistribution(5.0, 1.0)
//    private var lastProcessInstanceStart = Instant.now()

    fun runSingleTrace() = sequence {
        while (true) {
//            lastProcessInstanceStart += Duration.ofMinutes(processInstanceStartTime.sample().toLong())
//            var activitiesAvailableSince = emptyMap<Activity, Instant>()
            var predecessingActivities = emptyMap<Activity, ActivityInstance?>()
//            val processModelInstance =  processModel.createInstance()
            with(processModelInstance) { // setting the state to 'null' restarts the model
                setState(null)
                val trace = mutableListOf<ActivityInstance>()
                var lastExecutedActivityInstance: ActivityInstance? = null

                while (!isFinalState) {
                    run nextActivity@ {
                        val possibleActivities = availableActivities.toList()
//                        val lastActivityCompletionTime = (trace.lastOrNull()?.second ?: lastProcessInstanceStart) + (lastExecutedActivityDuration ?: Duration.ZERO)
//                        val lastActivityInstance = trace.lastOrNull() ?: lastExecutedActivityInstance
                        predecessingActivities = possibleActivities.map { activity -> activity to (predecessingActivities[activity] ?: lastExecutedActivityInstance) }.toMap()

//                        lastExecutedActivityDuration = Duration.ofMinutes(normalDistribution.sample().toLong())

                        if (possibleActivities.size == 1) {
                            val activity = possibleActivities[0]
                            getExecutionFor(activity).execute()
                            lastExecutedActivityInstance = ActivityInstance(activity, predecessingActivities[activity])
                            trace.add(lastExecutedActivityInstance!!)
                        } else {
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
//
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