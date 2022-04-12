package processm.enhancement.simulation

import processm.core.models.commons.Activity
import java.util.*

data class ActivityInstance(val activity: Activity, val executeAfter: ActivityInstance?) {
//    val id = UUID.randomUUID()
}