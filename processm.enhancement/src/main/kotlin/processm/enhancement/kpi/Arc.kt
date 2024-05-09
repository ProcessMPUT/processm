package processm.enhancement.kpi

import kotlinx.serialization.Serializable
import processm.core.models.commons.Activity
import processm.core.models.commons.CausalArc

/**
 * The arc between activities in a process model.
 */
@Serializable
data class Arc(override val source: Activity, override val target: Activity) : CausalArc {
    override fun toString(): String = "$source->$target"
}
