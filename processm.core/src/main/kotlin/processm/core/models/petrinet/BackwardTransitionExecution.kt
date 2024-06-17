package processm.core.models.petrinet

import processm.core.models.commons.ActivityExecution
import java.util.*

/**
 * A possible backward transition execution. The call to the [execute] fires the transition and changes the marking of
 * the corresponding Petri net.
 */
class BackwardTransitionExecution(
    override val activity: Transition,
    /**
     * The collection of activities that were the direct cause for executing [activity]. This may be an overestimate
     * if model representation does not allow for exact identification of the cause.
     * Note that, as this is backward execution, the [cause] refers to the following transitions in the model.
     */
    override val cause: Array<out Transition>,
    private val marking: Marking
) : ActivityExecution {
    override fun execute() {
        // consume tokens
        for (place in activity.outPlaces) {
            marking.compute(place) { p, old ->
                require(old !== null && old.isNotEmpty()) { "The place $p misses the token required to backward execute $activity." }
                old!!.removeLast()
                if (old.isEmpty()) null else old
            }
        }

        // produce tokens
        val sharedToken = Token(activity) // save memory; tokens are immutable and have no identity anyway
        for (place in activity.inPlaces) {
            marking.compute(place) { _, old ->
                (old ?: ArrayDeque()).apply { add(sharedToken) }
            }
        }
    }
}
