package processm.core.log

import processm.core.helpers.toUUID
import processm.core.log.attribute.Attribute
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
        conceptName = attributesInternal[nameMap["concept:name"]]?.getValue()?.toString()
        costTotal = attributesInternal[nameMap["cost:total"]]?.getValue()
            ?.let { it as? Double ?: it.toString().toDoubleOrNull() }
        costCurrency = attributesInternal[nameMap["cost:currency"]]?.getValue()?.toString()
        identityId = attributesInternal[nameMap["identity:id"]]?.getValue()
            ?.let { it as? UUID ?: runCatching { it.toString().toUUID() }.getOrNull() }
    }

    override fun setCustomAttributes(nameMap: Map<String, String>) {
        setCustomAttribute(conceptName, "concept:name", ::StringAttr, nameMap)
        setCustomAttribute(costTotal, "cost:total", ::RealAttr, nameMap)
        setCustomAttribute(costCurrency, "cost:currency", ::StringAttr, nameMap)
        setCustomAttribute(identityId, "identity:id", ::IDAttr, nameMap)
    }
}
