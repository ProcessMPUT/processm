package processm.core.models.footprint

import processm.core.models.commons.Activity
import processm.core.models.commons.ProcessModelState

/**
 * Represents the activity in a [Footprint] matrix.
 */
data class FootprintActivity(override val name: String) : Activity, ProcessModelState {
    override fun copy(): ProcessModelState = FootprintActivity(name)
    override fun toString(): String = name
}
