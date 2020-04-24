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
    override val attributesInternal: MutableMap<String, Attribute<*>> = HashMap()

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
     * The version of the XES standard this log conforms to (e.g., 1.0).
     */
    var xesVersion: String? = null
        internal set

    /**
     * A whitespace-separated list of optional XES features this log makes use of (e.g., nested-attributes).
     * If no optional features are used, this attribute shall have an empty value.
     */
    var xesFeatures: String? = null
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

    /**
     * Equals if both are Log and contains the same attributes
     */
    override fun equals(other: Any?): Boolean {
        if (other === this) return true
        if (other !is Log) return false
        return xesVersion == other.xesVersion && xesFeatures == other.xesFeatures && extensionsInternal == other.extensionsInternal
                && traceGlobalsInternal == other.traceGlobalsInternal && eventGlobalsInternal == other.eventGlobalsInternal
                && traceClassifiersInternal == other.traceClassifiersInternal && eventClassifiersInternal == other.eventClassifiersInternal
                && attributesInternal == other.attributesInternal
    }

    override fun hashCode(): Int {
        var result = extensionsInternal.hashCode()
        result = 31 * result + traceGlobalsInternal.hashCode()
        result = 31 * result + eventGlobalsInternal.hashCode()
        result = 31 * result + traceClassifiersInternal.hashCode()
        result = 31 * result + eventClassifiersInternal.hashCode()
        result = 31 * result + attributesInternal.hashCode()
        result = 31 * result + (xesFeatures?.hashCode() ?: 0)
        result = 31 * result + (xesVersion?.hashCode() ?: 0)
        return result
    }
}