package processm.core.log

import processm.core.log.attribute.Attribute
import java.util.*
import kotlin.collections.HashMap

/**
 * Event component
 *
 * Captures the event component from the XES metadata structure.
 */
class Event : XESElement() {
    override val attributesInternal: MutableMap<String, Attribute<*>> = HashMap()
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
    var timeTimestamp: Date? = null
        internal set
}