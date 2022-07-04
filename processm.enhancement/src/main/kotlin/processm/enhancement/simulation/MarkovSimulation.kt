package processm.enhancement.simulation

import processm.core.helpers.map2d.DoublingMap2D
import processm.core.helpers.map2d.Map2D
import processm.core.log.*
import processm.core.log.attribute.Attribute
import processm.core.log.attribute.Attribute.Companion.CONCEPT_INSTANCE
import processm.core.log.attribute.Attribute.Companion.CONCEPT_NAME
import processm.core.log.attribute.Attribute.Companion.IDENTITY_ID
import processm.core.log.attribute.IDAttr
import processm.core.log.attribute.StringAttr
import processm.core.models.commons.Activity
import processm.core.models.commons.ProcessModel
import java.util.*
import kotlin.random.Random

/**
 * Generates a log from a business [processModel] using the Markov simulation.
 * @param processModel the process model to generate traces for.
 * @param activityTransitionsProbabilityWeights for every pair of activities, it defines a weight
 * which is proportional to the probability that the transition between activities occurs.
 */
class MarkovSimulation(
    private val processModel: ProcessModel,
    private val activityTransitionsProbabilityWeights: Map2D<String, String, Double> = DoublingMap2D()
) : XESInputStream {

    private val random = Random.Default
    private val processModelInstance = processModel.createInstance()

    /**
     * Produces an artificial log from the [processModel]. Every event has `concept:name`, `concept:instance`, and
     * `identity:id` attributes set to the activity name, the activity instance id, and the event id, respectively.
     * The optional `cause` attribute consists of the identity:id of the direct cause event for this event (the
     * event with the preceding activity in the model).
     */
    override fun iterator(): Iterator<XESComponent> = sequence {
        yield(Log())

        var lastTraceId = 0L
        var lastEventId = 0L

        while (true) {
            yield(
                Trace(
                    mutableMapOf(
                        CONCEPT_NAME to StringAttr(CONCEPT_NAME, (++lastTraceId).toString()),
                        IDENTITY_ID to IDAttr(IDENTITY_ID, UUID(1L, lastTraceId))
                    )
                )
            )

            // Contains (stored as keys) currently available activities. For every available activity,
            // it contains preceding activity which execution caused the activity to be available.
            // Once Issue#145 is implemented, the collection is no longer necessary.
            var precedingActivities = emptyMap<Activity, Event?>()
            val conceptInstances = HashMap<String, Int>()

            with(processModelInstance) {
                // setting the state to 'null' restarts the model instance
                setState(null)
                val events = mutableListOf<Event>()
                var lastEvent: Event? = null

                while (!isFinalState) {
                    val activity = run nextActivity@{
                        val possibleActivities = availableActivities.toList()

                        precedingActivities = possibleActivities.associateWith { activity ->
                            (precedingActivities[activity] ?: lastEvent)
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
                                events.reversed().forEach { event ->

                                    val succeedingActivitiesWeights =
                                        possibleActivities.fold(mutableMapOf<Activity, Double>()) { result, activity ->
                                            activityTransitionsProbabilityWeights.getRow(event.conceptName!!)[activity.name]?.let { weight ->
                                                result[activity] = weight
                                            }

                                            return@fold result
                                        }

                                    val activitiesWeightsSum = succeedingActivitiesWeights.values.sum()
                                    var randomValue = random.nextDouble(activitiesWeightsSum)

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
                        val conceptInstance = conceptInstances.compute(activity.name) { _, old -> (old ?: 0) + 1 }
                        lastEvent = Event(
                            mutableMapOf<String, Attribute<*>>(
                                CONCEPT_NAME to StringAttr(CONCEPT_NAME, activity.name),
                                CONCEPT_INSTANCE to StringAttr(CONCEPT_INSTANCE, conceptInstance.toString()),
                                IDENTITY_ID to IDAttr(IDENTITY_ID, UUID(0L, ++lastEventId))
                            ).apply {
                                val prevId = precedingActivities[activity]?.identityId
                                if (prevId !== null)
                                    put(Attribute.CAUSE, IDAttr(Attribute.CAUSE, prevId))
                            }
                        )
                        events.add(lastEvent)
                    }
                }

                yieldAll(events)
            }
        }
    }.iterator()
}
