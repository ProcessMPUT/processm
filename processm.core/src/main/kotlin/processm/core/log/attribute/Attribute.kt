package processm.core.log.attribute

import java.time.Instant
import java.util.*

/**
 * The base class for the attribute compliant with the XES standard.
 */
object Attribute {

    /**
     * Stores a generally understood name for a log/trace/event.
     */
    const val CONCEPT_NAME = "concept:name"

    /**
     * This represents an identifier of the activity instance whose execution has generated the event.
     */
    const val CONCEPT_INSTANCE = "concept:instance"

    /**
     * The value contains the cost amount for a cost driver.
     */
    const val COST_AMOUNT = "cost:amount"

    /**
     * The currency (using the ISO 4217:2008 standard) of all costs of this trace/event.
     */
    const val COST_CURRENCY = "cost:currency"

    /**
     *  The value contains the id for the cost driver.
     */
    const val COST_DRIVER = "cost:driver"

    /**
     * A detailed list containing cost driver details.
     */
    const val COST_DRIVERS = "cost:drivers"

    /**
     * Total cost incurred for a trace/event. The value represents the sum of all the cost amounts within the element.
     */
    const val COST_TOTAL = "cost:total"

    /**
     * The value contains the cost type (e.g., Fixed, Overhead, Materials).
     */
    const val COST_TYPE = "cost:type"

    /**
     * The database identifier of an object.
     */
    const val DB_ID = "db:id"

    /**
     * Unique identifier (UUID) for the log/trace/event.
     */
    const val IDENTITY_ID = "identity:id"

    /**
     * This attribute refers to the lifecycle transactional model used for all events in the log. If this
     * attribute has a value of "standard", the standard lifecycle transactional model of this extension is
     * assumed. If it is has a value of "bpaf", the Business Process Analytics Format (BPAF) lifecycle transactional
     * model is assumed.
     */
    const val LIFECYCLE_MODEL = "lifecycle:model"

    /**
     * The transition attribute is defined for events, and specifies the lifecycle transition of each event.
     */
    const val LIFECYCLE_TRANSITION = "lifecycle:transition"

    /**
     * The state attribute is defined for events, and specifies the lifecycle state of each event.
     */
    const val LIFECYCLE_STATE = "lifecycle:state"

    /**
     * The group within the organizational structure, of which the resource that triggered the event is a member.
     */
    const val ORG_GROUP = "org:group"

    /**
     * The name, or identifier, of the resource that triggered the event.
     */
    const val ORG_RESOURCE = "org:resource"

    /**
     * The role of the resource that triggered the event, within the organizational structure.
     */
    const val ORG_ROLE = "org:role"

    /**
     * The UTC time at which the event occurred.
     */
    const val TIME_TIMESTAMP = "time:timestamp"

    /**
     * The version of XES standard.
     */
    const val XES_VERSION = "xes:version"

    /**
     * The features used in the holding log.
     */
    const val XES_FEATURES = "xes:features"
}

fun Any?.deepEquals(other: Any?): Boolean {
    if (this === null)
        return other === null
    if (this is Boolean || this is Instant || this is UUID || this is Long || this is Double || this is String || this is Tag) {
        return this == other
    }
    if (this is AttributeMap) {
        return other is AttributeMap && this.flat == other.flat && this.flat.entries.all { (k, v) -> v.deepEquals(other[k]) }
    }
    return false
}