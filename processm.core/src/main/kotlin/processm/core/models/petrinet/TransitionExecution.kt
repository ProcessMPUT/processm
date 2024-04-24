package processm.core.models.petrinet

import processm.core.models.commons.Activity
import processm.core.models.commons.ActivityExecution
import java.util.*

/**
 * A possible transition execution. The call to the [execute] fires the transition and changes the marking of
 * the corresponding Petri net.
 */
class TransitionExecution internal constructor(
    override val activity: Transition,
    override val cause: Collection<Activity>,
    private val marking: Marking
) : ActivityExecution {

    override fun execute() {
        // consume tokens
        for (place in activity.inPlaces) {
            marking.compute(place) { p, old ->
                require(old !== null && old.isNotEmpty()) { "The place $p misses the token required to execute $activity." }
                old!!.removeFirst()
                if (old.isEmpty()) null else old
            }
        }

        // produce tokens
        val sharedToken = Token(activity) // save memory; tokens are immutable and have no identity anyway
        for (place in activity.outPlaces) {
            marking.compute(place) { _, old ->
                (old ?: ArrayDeque()).apply { addLast(sharedToken) }
            }
        }
    }
}
