package processm.core.log

import processm.core.helpers.map2d.DoublingMap2D
import processm.core.log.attribute.StringAttr
import processm.core.models.commons.Activity
import processm.core.models.petrinet.Marking
import processm.core.models.petrinet.PetriNetInstance
import processm.core.models.petrinet.Transition
import processm.core.models.petrinet.petrinet
import java.util.*

/**
 * A XES stream that extends the events in the given [base] stream with concept:instance attribute inferred from
 * concept:name and lifecycle:transition, assuming that the "standard" lifecycle model from the IEEE1849-2016 standard
 * is in use. The existing values of concept:instance remain unaffected and guide the inference of the concept:instance
 * attribute for the events missing this attribute.
 */
class InferConceptInstanceFromStandardLifecycle(val base: XESInputStream) : XESInputStream {
    companion object {
        private const val CONCEPT_INSTANCE = "concept:instance"

        /**
         * The "standard" lifecycle model from IEEE-1849-2016 encoded as a Petri net.
         * See IEEE1849-2016 standard Section 7.2 / Figure 5.
         */
        private val standardLifecycle = petrinet {
            P tout "schedule" * "autoskip" // start
            P tin "schedule" tout "assign" * "_withdraw1" * "_manualskip1" * "_pi_abort1"// ready
            P tin "assign" * "reassign" tout "reassign" * "start" * "_withdraw2" * "_manualskip2" * "_pi_abort2" // assigned
            P tin "start" * "resume" tout "complete" * "suspend" * "_pi_abort3" * "_ate_abort1" // in progress
            P tin "suspend" tout "resume" * "_pi_abort4" * "_ate_abort2" // suspended
            P tin "_withdraw1" * "_withdraw2" tout "withdraw"
            P tin "_manualskip1" * "_manualskip2" tout "manualskip"
            P tin "_pi_abort1" * "_pi_abort2" * "_pi_abort3" * "_pi_abort4" tout "pi_abort"
            P tin "_ate_abort1" * "_ate_abort2" tout "ate_abort"
            P tin "autoskip" * "withdraw" * "manualskip" * "pi_abort" * "ate_abort" * "complete" // end
        }

        private val transitions = standardLifecycle.transitions.filter { !it.isSilent }.associateBy { it.name }
    }

    override fun iterator(): Iterator<XESComponent> = sequence<XESComponent> {
        // key1: activity name, key2: concept:instance, value: the current state of the lifecycle model
        val activityInstanceToLifecycle = DoublingMap2D<String, String, PetriNetInstance>()
        for (component in base) {
            when (component) {
                is Log, is Trace -> {
                    // start over when new log/trace occurs
                    activityInstanceToLifecycle.clear()
                    yield(component)
                }
                is Event -> {
                    if (component.conceptName.isNullOrEmpty() || component.lifecycleTransition !in transitions) {
                        yield(component)
                    } else if (!component.conceptInstance.isNullOrEmpty()) {
                        // set activity lifecycle state
                        activityInstanceToLifecycle.compute(
                            component.conceptName!!,
                            component.conceptInstance!!
                        ) { _, _, old ->
                            val marking =
                                Marking(transitions[component.lifecycleTransition!!]!!.outPlaces.associateWith { 1 })
                            (old ?: standardLifecycle.createInstance()).apply { setState(marking) }
                        }
                    } else {
                        // increment conceptInstance if the existing instance does not allow to run the given lifecycle:transition
                        var conceptInstance = 1
                        while (true) {
                            val lifecycleModel = activityInstanceToLifecycle.compute(
                                component.conceptName!!,
                                conceptInstance.toString()
                            ) { _, _, old -> (old ?: standardLifecycle.createInstance()) }!!

                            // verify whether the state of the current lifecycle model allows to run lifecycle:transition
                            val marking = findPath(lifecycleModel, transitions[component.lifecycleTransition!!]!!)
                            if (marking === null) {
                                // do not allow -> try with another instance
                                conceptInstance += 1
                            } else {
                                // allow -> update model state and exit
                                lifecycleModel.setState(marking)
                                break
                            }
                        }

                        val attributes = HashMap(component.attributes)
                        attributes[CONCEPT_INSTANCE] = StringAttr(CONCEPT_INSTANCE, conceptInstance.toString())
                        yield(Event(attributes))
                    }
                }
                else -> throw IllegalArgumentException("Unrecognized XES component $component.")
            }
        }
    }.iterator()

    /**
     * Looks for the way to execute [transition] given the state of [lifecycleModel] using Breadth First Search.
     */
    private fun findPath(lifecycleModel: PetriNetInstance, transition: Transition): Marking? {
        val initialMarking = lifecycleModel.currentState

        val queue = PriorityQueue<SearchState>()
        queue.add(SearchState(lifecycleModel.currentState.copy()))

        while (queue.isNotEmpty()) {
            val state = queue.poll()

            lifecycleModel.setState(state.marking.copy())

            if (state.activity !== null) {
                lifecycleModel.getExecutionFor(state.activity).execute()
                if (state.activity == transition)
                    return lifecycleModel.currentState
            }

            for (activity in lifecycleModel.availableActivities) {
                queue.add(
                    SearchState(
                        marking = lifecycleModel.currentState,
                        activity = activity,
                        cost = state.cost + (if (activity == transition) 0 else 1)
                    )
                )
            }
        }

        // revert model state
        lifecycleModel.setState(initialMarking)
        return null // not found
    }

    private data class SearchState(
        /**
         * The marking before executing activity.
         */
        val marking: Marking,
        val activity: Activity? = null,
        val cost: Int = 0
    ) : Comparable<SearchState> {
        override fun compareTo(other: SearchState): Int = cost.compareTo(other.cost)
    }
}