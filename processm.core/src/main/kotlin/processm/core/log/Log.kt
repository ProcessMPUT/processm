package processm.core.log

import processm.core.log.attribute.Attribute
import java.util.*
import kotlin.collections.HashMap

/**
 * Log element
 *
 * Captures the log component from the XES metadata structure.
 */
open class Log : XESElement() {
    internal val extensionsInternal: MutableMap<String, Extension> = HashMap()
    internal val traceGlobalsInternal: MutableMap<String, Attribute<*>> = HashMap()
    internal val eventGlobalsInternal: MutableMap<String, Attribute<*>> = HashMap()
    internal val traceClassifiersInternal: MutableMap<String, Classifier> = HashMap()
    internal val eventClassifiersInternal: MutableMap<String, Classifier> = HashMap()
    internal override val attributesInternal: MutableMap<String, Attribute<*>> = HashMap()

    /**
     * Extensions declared in the log file.
     */
    val extensions: Map<String, Extension>
        get() = Collections.unmodifiableMap(extensionsInternal)
    /**
     * Global trace attributes for the log.
     */
    val traceGlobals: Map<String, Attribute<*>>
        get() = Collections.unmodifiableMap(traceGlobalsInternal)
    /**
     * Global event attributes for the log.
     */
    val eventGlobals: Map<String, Attribute<*>>
        get() = Collections.unmodifiableMap(eventGlobalsInternal)
    /**
     * Trace classifiers for the log.
     */
    val traceClassifiers: Map<String, Classifier>
        get() = Collections.unmodifiableMap(traceClassifiersInternal)
    /**
     * Event classifiers for the log.
     */
    val eventClassifiers: Map<String, Classifier>
        get() = Collections.unmodifiableMap(eventClassifiersInternal)
    /**
     * A whitespace-separated list of optional XES features this document makes use of (e.g., nested-attributes).
     * If no optional features are used, this attribute shall have an empty value.
     */
    var features: String? = null
        internal set
    /**
     * Special attribute based on concept:name
     * Standard extension: Concept
     */
    override var conceptName: String? = null
        internal set
    /**
     * Special attribute based on identity:id
     * Standard extension: Identity
     */
    override var identityId: String? = null
        internal set
    /**
     * Special attribute based on lifecycle:model
     * Standard extension: Lifecycle
     */
    var lifecycleModel: String? = null
        internal set(value) {
            field = value?.intern()
        }
}