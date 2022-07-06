package processm.enhancement.simulation

import processm.core.models.commons.Activity

/**
 * Represents an instance of activity.
 * @property activity A type of activity.
 * @property executeAfter An activity instance which the current activity depends on.
 */
data class ActivityInstance(val activity: Activity, val executeAfter: ActivityInstance?)
