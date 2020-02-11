package processm.core.log

import processm.core.log.attribute.Attribute
import java.util.*

/**
 * Trace element
 *
 * Captures the trace component from the XES metadata structure.
 */
class Trace : XESElement {
    override val attributesInternal: MutableMap<String, Attribute<*>> = TreeMap(String.CASE_INSENSITIVE_ORDER)
    /**
     * Special attribute based on concept:name
     * Standard extension: Concept
     */
    var conceptName: String? = null
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
    var identityId: String? = null
        internal set
    /**
     * Event stream special tag - true if trace is fake and log contains only events (no trace)
     */
    var isEventStream: Boolean = false
        internal set
    /**
     * Extra attributes declared for trace element
     */
    val attributes: Map<String, Attribute<*>>
        get() = Collections.unmodifiableMap(attributesInternal)
}