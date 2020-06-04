package processm.core.log

import processm.core.log.attribute.Attribute
import java.util.*

/**
 * XES Element like Log, Trace or Event
 *
 * Inside element we expect to store attributes.
 */
abstract class XESElement {
    /**
     * Special attribute based on concept:name
     * Standard extension: Concept
     */
    abstract val conceptName: String?

    /**
     * Special attribute based on identity:id
     * Standard extension: Identity
     */
    abstract val identityId: String?

    /**
     * The number of elements (logs, traces, events) represented by this object.
     * This property is designed for use with grouping, where it holds the number of underlying elements. E.g.,
     * for [Trace] variant, it refers to the number of actual traces compliant with this variant.
     * This property is useful for calculating the support for a log/trace/event variant.
     */
    var count: Long = 1L
        internal set

    /**
     * Collection of all attributes associated with this element.
     */
    val attributes: Map<String, Attribute<*>>
        get() = Collections.unmodifiableMap(attributesInternal)

    /**
     * A backing mutable field for [attributes].
     */
    internal val attributesInternal: MutableMap<String, Attribute<*>> = HashMap()

    internal abstract fun setStandardAttributes(nameMap: Map<String, String>)
}