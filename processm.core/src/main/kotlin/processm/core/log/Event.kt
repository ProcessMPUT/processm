package processm.core.log

import java.time.Instant

/**
 * Event component
 *
 * Captures the event component from the XES metadata structure.
 */
class Event : XESElement() {
    /**
     * Special attribute based on concept:name
     * Standard extension: Concept
     */
    override var conceptName: String? = null
        internal set

    /**
     * Special attribute based on concept:instance
     * Standard extension: Concept
     */
    var conceptInstance: String? = null
        internal set

    /**
     * Special attribute based on cost:currency
     * Standard extension: Cost
     */
    var costCurrency: String? = null
        internal set

    /**
     * Special attribute based on cost:total
     * Standard extension: Cost
     */
    var costTotal: Double? = null
        internal set

    /**
     * Special attribute based on identity:id
     * Standard extension: Identity
     */
    override var identityId: String? = null
        internal set

    /**
     * Special attribute based on lifecycle:transition
     * Standard extension: Lifecycle
     */
    var lifecycleTransition: String? = null
        internal set(value) {
            field = value?.intern()
        }

    /**
     * Special attribute based on lifecycle:state
     * Standard extension: Lifecycle
     */
    var lifecycleState: String? = null
        internal set(value) {
            field = value?.intern()
        }

    /**
     * Special attribute based on org:resource
     * Standard extension: Org
     */
    var orgResource: String? = null
        internal set

    /**
     * Special attribute based on org:role
     * Standard extension: Org
     */
    var orgRole: String? = null
        internal set

    /**
     * Special attribute based on org:group
     * Standard extension: Org
     */
    var orgGroup: String? = null
        internal set

    /**
     * Special attribute based on time:timestamp
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
        conceptName = attributes[nameMap["concept:name"]]?.getValue() as String?
        conceptInstance = attributes[nameMap["concept:instance"]]?.getValue() as String?

        costTotal = attributes[nameMap["cost:total"]]?.getValue() as Double?
        costCurrency = attributes[nameMap["cost:currency"]]?.getValue() as String?

        identityId = attributes[nameMap["identity:id"]]?.getValue() as String?

        lifecycleState = attributes[nameMap["lifecycle:state"]]?.getValue() as String?
        lifecycleTransition = attributes[nameMap["lifecycle:transition"]]?.getValue() as String?

        orgRole = attributes[nameMap["org:role"]]?.getValue() as String?
        orgGroup = attributes[nameMap["org:group"]]?.getValue() as String?
        orgResource = attributes[nameMap["org:resource"]]?.getValue() as String?

        timeTimestamp = attributes[nameMap["time:timestamp"]]?.getValue() as Instant?
    }
}