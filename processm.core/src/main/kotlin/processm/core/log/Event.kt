package processm.core.log

import kotlinx.serialization.Serializable
import processm.core.helpers.parseISO8601
import processm.core.helpers.toUUID
import processm.core.log.attribute.Attribute.CONCEPT_INSTANCE
import processm.core.log.attribute.Attribute.CONCEPT_NAME
import processm.core.log.attribute.Attribute.COST_CURRENCY
import processm.core.log.attribute.Attribute.COST_TOTAL
import processm.core.log.attribute.Attribute.IDENTITY_ID
import processm.core.log.attribute.Attribute.LIFECYCLE_STATE
import processm.core.log.attribute.Attribute.LIFECYCLE_TRANSITION
import processm.core.log.attribute.Attribute.ORG_GROUP
import processm.core.log.attribute.Attribute.ORG_RESOURCE
import processm.core.log.attribute.Attribute.ORG_ROLE
import processm.core.log.attribute.Attribute.TIME_TIMESTAMP
import processm.core.log.attribute.MutableAttributeMap
import java.time.Instant
import java.util.*

/**
 * Event component
 *
 * Captures the event component from the XES metadata structure.
 */
@Serializable(with = EventSerializer::class)
open class Event(
    attributesInternal: MutableAttributeMap = MutableAttributeMap()
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

    init {
        lateInit()
    }

    /**
     * Equals if both are Event and contains the same attributes
     */
    override fun equals(other: Any?): Boolean = other === this
            || other is Event
            && attributesInternal == other.attributesInternal

    override fun hashCode(): Int = attributesInternal.hashCode()

    override fun setStandardAttributes(nameMap: Map<String, String>) {
        conceptName = attributesInternal.getOrNull(nameMap[CONCEPT_NAME])?.toString()
        conceptInstance = attributesInternal.getOrNull(nameMap[CONCEPT_INSTANCE])?.toString()

        costTotal = attributesInternal.getOrNull(nameMap[COST_TOTAL])
            ?.let { it as? Double ?: it.toString().toDoubleOrNull() }
        costCurrency = attributesInternal.getOrNull(nameMap[COST_CURRENCY])?.toString()

        identityId = attributesInternal.getOrNull(nameMap[IDENTITY_ID])
            ?.let { it as? UUID ?: runCatching { it.toString().toUUID() }.getOrNull() }

        lifecycleState = attributesInternal.getOrNull(nameMap[LIFECYCLE_STATE])?.toString()
        lifecycleTransition = attributesInternal.getOrNull(nameMap[LIFECYCLE_TRANSITION])?.toString()

        orgRole = attributesInternal.getOrNull(nameMap[ORG_ROLE])?.toString()
        orgGroup = attributesInternal.getOrNull(nameMap[ORG_GROUP])?.toString()
        orgResource = attributesInternal.getOrNull(nameMap[ORG_RESOURCE])?.toString()

        timeTimestamp = attributesInternal.getOrNull(nameMap[TIME_TIMESTAMP])
            ?.let { it as? Instant ?: runCatching { it.toString().parseISO8601() }.getOrNull() }
    }

    override fun setCustomAttributes(nameMap: Map<String, String>) {
        setCustomAttribute(conceptName, CONCEPT_NAME, nameMap)
        setCustomAttribute(conceptInstance, CONCEPT_INSTANCE, nameMap)

        setCustomAttribute(costTotal, COST_TOTAL, nameMap)
        setCustomAttribute(costCurrency, COST_CURRENCY, nameMap)

        setCustomAttribute(identityId, IDENTITY_ID, nameMap)

        setCustomAttribute(lifecycleState, LIFECYCLE_STATE, nameMap)
        setCustomAttribute(lifecycleTransition, LIFECYCLE_TRANSITION, nameMap)

        setCustomAttribute(orgRole, ORG_ROLE, nameMap)
        setCustomAttribute(orgGroup, ORG_GROUP, nameMap)
        setCustomAttribute(orgResource, ORG_RESOURCE, nameMap)

        setCustomAttribute(timeTimestamp, TIME_TIMESTAMP, nameMap)
    }
}
