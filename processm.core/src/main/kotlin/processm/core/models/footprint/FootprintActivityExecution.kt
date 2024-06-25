package processm.core.models.footprint

import processm.core.models.commons.Activity
import processm.core.models.commons.ActivityExecution

/**
 * Represents and activity execution for a [Footprint] matrix.
 */
class FootprintActivityExecution(
    override val activity: FootprintActivity,
    val instance: FootprintInstance
) : ActivityExecution {
    override val cause: Array<out Activity> =
        if (instance.currentState is FootprintActivity) arrayOf(instance.currentState as FootprintActivity)
        else emptyArray()

    override fun execute() {
        instance.currentState = activity
    }
}
