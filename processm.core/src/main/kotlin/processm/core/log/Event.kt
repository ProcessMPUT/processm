package processm.core.log

import processm.core.helpers.parseISO8601
import processm.core.helpers.toUUID
import processm.core.log.attribute.*
import processm.core.log.attribute.Attribute.Companion.CONCEPT_INSTANCE
import processm.core.log.attribute.Attribute.Companion.CONCEPT_NAME
import processm.core.log.attribute.Attribute.Companion.COST_CURRENCY
import processm.core.log.attribute.Attribute.Companion.COST_TOTAL
import processm.core.log.attribute.Attribute.Companion.IDENTITY_ID
import processm.core.log.attribute.Attribute.Companion.LIFECYCLE_STATE
import processm.core.log.attribute.Attribute.Companion.LIFECYCLE_TRANSITION
import processm.core.log.attribute.Attribute.Companion.ORG_GROUP
import processm.core.log.attribute.Attribute.Companion.ORG_RESOURCE
import processm.core.log.attribute.Attribute.Companion.ORG_ROLE
import processm.core.log.attribute.Attribute.Companion.TIME_TIMESTAMP
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
        conceptName = attributesInternal[nameMap[CONCEPT_NAME]]?.getValue()?.toString()
        conceptInstance = attributesInternal[nameMap[CONCEPT_INSTANCE]]?.getValue()?.toString()

        costTotal = attributesInternal[nameMap[COST_TOTAL]]?.getValue()
            ?.let { it as? Double ?: it.toString().toDoubleOrNull() }
        costCurrency = attributesInternal[nameMap[COST_CURRENCY]]?.getValue()?.toString()

        identityId = attributesInternal[nameMap[IDENTITY_ID]]?.getValue()
            ?.let { it as? UUID ?: runCatching { it.toString().toUUID() }.getOrNull() }

        lifecycleState = attributesInternal[nameMap[LIFECYCLE_STATE]]?.getValue()?.toString()
        lifecycleTransition = attributesInternal[nameMap[LIFECYCLE_TRANSITION]]?.getValue()?.toString()

        orgRole = attributesInternal[nameMap[ORG_ROLE]]?.getValue()?.toString()
        orgGroup = attributesInternal[nameMap[ORG_GROUP]]?.getValue()?.toString()
        orgResource = attributesInternal[nameMap[ORG_RESOURCE]]?.getValue()?.toString()

        timeTimestamp = attributesInternal[nameMap[TIME_TIMESTAMP]]?.getValue()
            ?.let { it as? Instant ?: runCatching { it.toString().parseISO8601() }.getOrNull() }
    }

    override fun setCustomAttributes(nameMap: Map<String, String>) {
        setCustomAttribute(conceptName, CONCEPT_NAME, ::StringAttr, nameMap)
        setCustomAttribute(conceptInstance, CONCEPT_INSTANCE, ::StringAttr, nameMap)

        setCustomAttribute(costTotal, COST_TOTAL, ::RealAttr, nameMap)
        setCustomAttribute(costCurrency, COST_CURRENCY, ::StringAttr, nameMap)

        setCustomAttribute(identityId, IDENTITY_ID, ::IDAttr, nameMap)

        setCustomAttribute(lifecycleState, LIFECYCLE_STATE, ::StringAttr, nameMap)
        setCustomAttribute(lifecycleTransition, LIFECYCLE_TRANSITION, ::StringAttr, nameMap)

        setCustomAttribute(orgRole, ORG_ROLE, ::StringAttr, nameMap)
        setCustomAttribute(orgGroup, ORG_GROUP, ::StringAttr, nameMap)
        setCustomAttribute(orgResource, ORG_RESOURCE, ::StringAttr, nameMap)

        setCustomAttribute(timeTimestamp, TIME_TIMESTAMP, ::DateTimeAttr, nameMap)
    }
}
