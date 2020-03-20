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
     * Collection of all attributes associated with this element.
     */
    val attributes: Map<String, Attribute<*>>
        get() = Collections.unmodifiableMap(attributesInternal)

    /**
     * A backing mutable field for [attributes].
     */
    internal abstract val attributesInternal: MutableMap<String, Attribute<*>>
}