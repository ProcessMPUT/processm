package processm.core.models.footprint

import processm.core.models.commons.ActivityExecution

/**
 * Represents and activity execution for a [Footprint] matrix.
 */
class FootprintActivityExecution(
    override val activity: FootprintActivity,
    val instance: FootprintInstance
) : ActivityExecution {
    override fun execute() {
        instance.currentState = activity
    }
}
