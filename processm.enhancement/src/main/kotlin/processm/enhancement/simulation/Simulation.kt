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

class Simulation(private val processModelInstance: ProcessModelInstance, private val nextActivityCumulativeDistributionFunction: Map<Activity, Set<Map<Activity, Double>>> = emptyMap(), private val processInstanceStartTime: RealDistribution) {
    private val random = Random.Default
    private val normalDistribution = NormalDistribution(5.0, 1.0)
    private var lastProcessInstanceStart = Instant.now()

    fun runSingleTrace() = sequence {
        while (true) {
            lastProcessInstanceStart += Duration.ofMinutes(processInstanceStartTime.sample().toLong())
            var activitiesAvailableSince = emptyMap<Activity, Instant>()
//            val processModelInstance = processModel.createInstance()
            with(processModelInstance) { // setting the state to 'null' restarts the model
//                setState(null)
                val trace = mutableListOf<Pair<Activity, Instant>>()
                var lastExecutedActivityDuration: Duration? = null

                while (!isFinalState) {
                    run nextActivity@ {
                        val possibleActivities = availableActivities.toList()
                        val lastActivityCompletionTime = (trace.lastOrNull()?.second ?: lastProcessInstanceStart) + (lastExecutedActivityDuration ?: Duration.ZERO)

                        activitiesAvailableSince = possibleActivities.map { activity -> activity to (activitiesAvailableSince[activity] ?: lastActivityCompletionTime) }.toMap()

                        lastExecutedActivityDuration = Duration.ofMinutes(normalDistribution.sample().toLong())

                        if (possibleActivities.size == 1) {
                            getExecutionFor(possibleActivities[0]).execute()
                            trace.add(possibleActivities[0] to activitiesAvailableSince[possibleActivities[0]]!!.plus(lastExecutedActivityDuration))
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
                                            trace.add(nextActivity to activitiesAvailableSince[nextActivity]!!.plus(lastExecutedActivityDuration))
                                            return@nextActivity
                                        }
                                    }
                                }
                            }
//
                            val randomNextActivity = possibleActivities[random.nextInt(possibleActivities.size)]
                            getExecutionFor(randomNextActivity).execute()
                            trace.add(randomNextActivity to activitiesAvailableSince[randomNextActivity]!!.plus(lastExecutedActivityDuration))
                        }
                    }
                }

                yield(trace.toList())
            }
        }
    }
}