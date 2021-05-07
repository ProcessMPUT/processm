package processm.core.models.petrinet

import processm.core.models.commons.ActivityExecution

/**
 * A possible transition execution. The call to the [execute] fires the transition and changes the marking of
 * the corresponding Petri net.
 */
class TransitionExecution internal constructor(
    override val activity: Transition,
    private val marking: Marking
) : ActivityExecution {
    override fun execute() {
        // consume tokens
        for (place in activity.inPlaces) {
            marking.compute(place) { p, old ->
                requireNotNull(old) { "The place $p misses the token required to execute $activity." }
                if (old == 1) null else old - 1
            }
        }

        // produce tokens
        for (place in activity.outPlaces) {
            marking.compute(place) { _, old ->
                if (old === null) 1 else old + 1
            }
        }
    }
}
