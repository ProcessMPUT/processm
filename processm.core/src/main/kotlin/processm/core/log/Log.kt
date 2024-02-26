package processm.core.log

import processm.core.log.attribute.Attribute.CONCEPT_NAME
import processm.core.log.attribute.Attribute.IDENTITY_ID
import processm.core.log.attribute.Attribute.LIFECYCLE_MODEL
import processm.core.log.attribute.AttributeMap
import processm.core.log.attribute.MutableAttributeMap
import processm.core.log.attribute.unmodifiableView
import processm.helpers.toUUID
import java.util.*

/**
 * Log element
 *
 * Captures the log component from the XES metadata structure.
 */
open class Log(
    attributesInternal: MutableAttributeMap = MutableAttributeMap(),
    internal val extensionsInternal: MutableMap<String, Extension> = HashMap(),
    internal val traceGlobalsInternal: MutableAttributeMap = MutableAttributeMap(),
    internal val eventGlobalsInternal: MutableAttributeMap = MutableAttributeMap(),
    internal val traceClassifiersInternal: MutableMap<String, Classifier> = HashMap(),
    internal val eventClassifiersInternal: MutableMap<String, Classifier> = HashMap()
) : XESComponent(attributesInternal) {
    /**
     * Extensions declared in the log file.
     */
    val extensions: Map<String, Extension>
        get() = Collections.unmodifiableMap(extensionsInternal)

    /**
     * Global trace attributes for the log.
     */
    val traceGlobals: AttributeMap
        get() = traceGlobalsInternal.unmodifiableView()

    /**
     * Global event attributes for the log.
     */
    val eventGlobals: AttributeMap
        get() = eventGlobalsInternal.unmodifiableView()

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
     * Standard attribute based on concept:name
     * Standard extension: Concept
     */
    override var conceptName: String? = null
        internal set

    /**
     * Standard attribute based on identity:id
     * Standard extension: Identity
     */
    override var identityId: UUID? = null
        internal set

    /**
     * Standard attribute based on lifecycle:model
     * Standard extension: Lifecycle
     */
    var lifecycleModel: String? = null
        internal set(value) {
            field = value?.intern()
        }

    init {
        lateInit()
    }

    /**
     * Equals if both are Log and contains the same attributes
     */
    override fun equals(other: Any?): Boolean = other === this
            || other is Log
            && xesVersion == other.xesVersion
            && xesFeatures == other.xesFeatures
            && extensionsInternal == other.extensionsInternal
            && traceGlobalsInternal == other.traceGlobalsInternal
            && eventGlobalsInternal == other.eventGlobalsInternal
            && traceClassifiersInternal == other.traceClassifiersInternal
            && eventClassifiersInternal == other.eventClassifiersInternal
            && attributesInternal == other.attributesInternal

    override fun hashCode(): Int = Objects.hash(
        xesVersion,
        xesFeatures,
        extensionsInternal,
        traceGlobalsInternal,
        eventGlobalsInternal,
        traceClassifiersInternal,
        eventClassifiersInternal,
        attributesInternal
    )

    override fun setStandardAttributes(nameMap: Map<String, String>) {
        conceptName = attributesInternal.getOrNull(nameMap[CONCEPT_NAME])?.toString()
        identityId = attributesInternal.getOrNull(nameMap[IDENTITY_ID])
            ?.let { it as? UUID ?: runCatching { it.toString().toUUID() }.getOrNull() }
        lifecycleModel = attributesInternal.getOrNull(nameMap[LIFECYCLE_MODEL])?.toString()
    }

    override fun setCustomAttributes(nameMap: Map<String, String>) {
        setCustomAttribute(conceptName, CONCEPT_NAME, nameMap)
        setCustomAttribute(identityId, IDENTITY_ID, nameMap)
        setCustomAttribute(lifecycleModel, LIFECYCLE_MODEL, nameMap)
    }
}
