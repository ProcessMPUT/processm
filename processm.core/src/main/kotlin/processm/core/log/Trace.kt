package processm.core.log

import java.util.*

/**
 * Trace element
 *
 * Captures the trace component from the XES metadata structure.
 */
open class Trace : XESElement() {
    /**
     * Special attribute based on concept:name
     * Standard extension: Concept
     */
    override var conceptName: String? = null
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
        conceptName = attributes[nameMap["concept:name"]]?.getValue() as String?
        costTotal = attributes[nameMap["cost:total"]]?.getValue() as Double?
        costCurrency = attributes[nameMap["cost:currency"]]?.getValue() as String?
        identityId = attributes[nameMap["identity:id"]]?.getValue() as String?
    }
}