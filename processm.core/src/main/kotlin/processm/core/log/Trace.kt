package processm.core.log

import processm.core.helpers.toUUID
import processm.core.log.attribute.Attribute.CONCEPT_NAME
import processm.core.log.attribute.Attribute.COST_CURRENCY
import processm.core.log.attribute.Attribute.COST_TOTAL
import processm.core.log.attribute.Attribute.IDENTITY_ID
import processm.core.log.attribute.MutableAttributeMap
import java.util.*

/**
 * Trace element
 *
 * Captures the trace component from the XES metadata structure.
 */
open class Trace(
    attributesInternal: MutableAttributeMap = MutableAttributeMap()
) : TraceOrEventBase(attributesInternal) {
    /**
     * Event stream special tag - true if trace is fake and log contains only events (no trace)
     */
    var isEventStream: Boolean = false
        internal set

    init {
        lateInit()
    }

    /**
     * Equals if both are Trace and contains the same attributes
     */
    override fun equals(other: Any?): Boolean = other === this
            || other is Trace
            && isEventStream == other.isEventStream
            && attributesInternal == other.attributesInternal

    override fun hashCode(): Int = Objects.hash(isEventStream, attributesInternal)

    override fun setStandardAttributes(nameMap: Map<String, String>) {
        conceptName = attributesInternal.getOrNull(nameMap[CONCEPT_NAME])?.toString()
        costTotal = attributesInternal.getOrNull(nameMap[COST_TOTAL])
            ?.let { it as? Double ?: it.toString().toDoubleOrNull() }
        costCurrency = attributesInternal.getOrNull(nameMap[COST_CURRENCY])?.toString()
        identityId = attributesInternal.getOrNull(nameMap[IDENTITY_ID])
            ?.let { it as? UUID ?: runCatching { it.toString().toUUID() }.getOrNull() }
    }

    override fun setCustomAttributes(nameMap: Map<String, String>) {
        setCustomAttribute(conceptName, CONCEPT_NAME, nameMap)
        setCustomAttribute(costTotal, COST_TOTAL, nameMap)
        setCustomAttribute(costCurrency, COST_CURRENCY, nameMap)
        setCustomAttribute(identityId, IDENTITY_ID, nameMap)
    }
}
