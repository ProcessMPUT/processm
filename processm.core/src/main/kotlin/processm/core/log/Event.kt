package processm.core.log

import java.time.Instant
import java.util.*

/**
 * Event component
 *
 * Captures the event component from the XES metadata structure.
 */
open class Event : TraceOrEventBase() {
    /**
     * Standard attribute based on concept:instance
     * Standard extension: Concept
     */
    var conceptInstance: String? = null
        internal set

    /**
     * Standard attribute based on lifecycle:transition
     * Standard extension: Lifecycle
     */
    var lifecycleTransition: String? = null
        internal set(value) {
            field = value?.intern()
        }

    /**
     * Standard attribute based on lifecycle:state
     * Standard extension: Lifecycle
     */
    var lifecycleState: String? = null
        internal set(value) {
            field = value?.intern()
        }

    /**
     * Standard attribute based on org:resource
     * Standard extension: Org
     */
    var orgResource: String? = null
        internal set

    /**
     * Standard attribute based on org:role
     * Standard extension: Org
     */
    var orgRole: String? = null
        internal set

    /**
     * Standard attribute based on org:group
     * Standard extension: Org
     */
    var orgGroup: String? = null
        internal set

    /**
     * Standard attribute based on time:timestamp
     * Standard extension: Time
     */
    var timeTimestamp: Instant? = null
        internal set

    /**
     * Equals if both are Event and contains the same attributes
     */
    override fun equals(other: Any?): Boolean = other === this
            || other is Event
            && attributesInternal == other.attributesInternal

    override fun hashCode(): Int = attributesInternal.hashCode()

    override fun setStandardAttributes(nameMap: Map<String, String>) {
        conceptName = attributesInternal[nameMap["concept:name"]]?.getValue() as String?
        conceptInstance = attributesInternal[nameMap["concept:instance"]]?.getValue() as String?

        costTotal = attributesInternal[nameMap["cost:total"]]?.getValue() as Double?
        costCurrency = attributesInternal[nameMap["cost:currency"]]?.getValue() as String?

        identityId = attributesInternal[nameMap["identity:id"]]?.getValue() as UUID?

        lifecycleState = attributesInternal[nameMap["lifecycle:state"]]?.getValue() as String?
        lifecycleTransition = attributesInternal[nameMap["lifecycle:transition"]]?.getValue() as String?

        orgRole = attributesInternal[nameMap["org:role"]]?.getValue() as String?
        orgGroup = attributesInternal[nameMap["org:group"]]?.getValue() as String?
        orgResource = attributesInternal[nameMap["org:resource"]]?.getValue() as String?

        timeTimestamp = attributesInternal[nameMap["time:timestamp"]]?.getValue() as Instant?
    }
}
