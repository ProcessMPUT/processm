package processm.enhancement.simulation

import processm.core.log.attribute.Attribute

/**
 * A custom XES attribute that consists of the identity:id of the direct cause event for this event (the
 * event with the preceding activity in the model).
 */
val Attribute.Companion.CAUSE: String
    get() = "cause"
