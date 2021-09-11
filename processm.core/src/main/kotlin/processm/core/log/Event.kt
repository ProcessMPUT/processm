package processm.core.log

import processm.core.helpers.parseISO8601
import processm.core.helpers.toUUID
import processm.core.log.attribute.*
import java.time.Instant
import java.util.*

/**
 * Event component
 *
 * Captures the event component from the XES metadata structure.
 */
open class Event(
    attributesInternal: MutableMap<String, Attribute<*>> = HashMap()
) : TraceOrEventBase(attributesInternal) {
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
        conceptName = attributesInternal[nameMap["concept:name"]]?.getValue()?.toString()
        conceptInstance = attributesInternal[nameMap["concept:instance"]]?.getValue()?.toString()

        costTotal = attributesInternal[nameMap["cost:total"]]?.getValue()
            ?.let { it as? Double ?: it.toString().toDoubleOrNull() }
        costCurrency = attributesInternal[nameMap["cost:currency"]]?.getValue()?.toString()

        identityId = attributesInternal[nameMap["identity:id"]]?.getValue()
            ?.let { it as? UUID ?: runCatching { it.toString().toUUID() }.getOrNull() }

        lifecycleState = attributesInternal[nameMap["lifecycle:state"]]?.getValue()?.toString()
        lifecycleTransition = attributesInternal[nameMap["lifecycle:transition"]]?.getValue()?.toString()

        orgRole = attributesInternal[nameMap["org:role"]]?.getValue()?.toString()
        orgGroup = attributesInternal[nameMap["org:group"]]?.getValue()?.toString()
        orgResource = attributesInternal[nameMap["org:resource"]]?.getValue()?.toString()

        timeTimestamp = attributesInternal[nameMap["time:timestamp"]]?.getValue()
            ?.let { it as? Instant ?: runCatching { it.toString().parseISO8601() }.getOrNull() }
    }

    override fun setCustomAttributes(nameMap: Map<String, String>) {
        setCustomAttribute(conceptName, "concept:name", ::StringAttr, nameMap)
        setCustomAttribute(conceptInstance, "concept:instance", ::StringAttr, nameMap)

        setCustomAttribute(costTotal, "cost:total", ::RealAttr, nameMap)
        setCustomAttribute(costCurrency, "cost:currency", ::StringAttr, nameMap)

        setCustomAttribute(identityId, "identity:id", ::IDAttr, nameMap)

        setCustomAttribute(lifecycleState, "lifecycle:state", ::StringAttr, nameMap)
        setCustomAttribute(lifecycleTransition, "lifecycle:transition", ::StringAttr, nameMap)

        setCustomAttribute(orgRole, "org:role", ::StringAttr, nameMap)
        setCustomAttribute(orgGroup, "org:group", ::StringAttr, nameMap)
        setCustomAttribute(orgResource, "org:resource", ::StringAttr, nameMap)

        setCustomAttribute(timeTimestamp, "time:timestamp", ::DateTimeAttr, nameMap)
    }
}
