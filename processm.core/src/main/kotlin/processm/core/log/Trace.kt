package processm.core.log

import processm.core.helpers.toUUID
import processm.core.log.attribute.Attribute
import processm.core.log.attribute.Attribute.Companion.CONCEPT_NAME
import processm.core.log.attribute.Attribute.Companion.COST_CURRENCY
import processm.core.log.attribute.Attribute.Companion.COST_TOTAL
import processm.core.log.attribute.Attribute.Companion.IDENTITY_ID
import processm.core.log.attribute.IDAttr
import processm.core.log.attribute.RealAttr
import processm.core.log.attribute.StringAttr
import java.util.*

/**
 * Trace element
 *
 * Captures the trace component from the XES metadata structure.
 */
open class Trace(
    attributesInternal: MutableMap<String, Attribute<*>> = HashMap()
) : TraceOrEventBase(attributesInternal) {
    /**
     * Event stream special tag - true if trace is fake and log contains only events (no trace)
     */
    var isEventStream: Boolean = false
        internal set

    /**
     * Equals if both are Trace and contains the same attributes
     */
    override fun equals(other: Any?): Boolean = other === this
            || other is Trace
            && isEventStream == other.isEventStream
            && attributesInternal == other.attributesInternal

    override fun hashCode(): Int = Objects.hash(isEventStream, attributesInternal)

    override fun setStandardAttributes(nameMap: Map<String, String>) {
        conceptName = attributesInternal[nameMap[CONCEPT_NAME]]?.getValue()?.toString()
        costTotal = attributesInternal[nameMap[COST_TOTAL]]?.getValue()
            ?.let { it as? Double ?: it.toString().toDoubleOrNull() }
        costCurrency = attributesInternal[nameMap[COST_CURRENCY]]?.getValue()?.toString()
        identityId = attributesInternal[nameMap[IDENTITY_ID]]?.getValue()
            ?.let { it as? UUID ?: runCatching { it.toString().toUUID() }.getOrNull() }
    }

    override fun setCustomAttributes(nameMap: Map<String, String>) {
        setCustomAttribute(conceptName, CONCEPT_NAME, ::StringAttr, nameMap)
        setCustomAttribute(costTotal, COST_TOTAL, ::RealAttr, nameMap)
        setCustomAttribute(costCurrency, COST_CURRENCY, ::StringAttr, nameMap)
        setCustomAttribute(identityId, IDENTITY_ID, ::IDAttr, nameMap)
    }
}
