package processm.core.log.extension

import java.util.*

/**
 * The XES Extention element (XESEXT) captures the extension defnition from the XES metadata structure.
 */
class Extension(name: String, prefix: String, uri: String) {
    /**
     * The name of the extension.
     * Format: NCName
     * Required: true
     */
    val name: String = name.intern()

    /**
     * The prefx to be used for this extension.
     * Format: NCName
     * Required: true
     */
    val prefix: String = prefix.intern()

    /**
     * The URI where this extension can be retrieved from.
     * Format: anyURI
     * Required: true
     */
    val uri: String = uri.intern()

    internal val logInternal: MutableMap<String, XesExtensionAttribute> = TreeMap()
    internal val traceInternal: MutableMap<String, XesExtensionAttribute> = TreeMap()
    internal val eventInternal: MutableMap<String, XesExtensionAttribute> = TreeMap()
    internal val metaInternal: MutableMap<String, XesExtensionAttribute> = TreeMap()

    /**
     * Captures the log extension defnition from the XES metadata structure.
     */
    val log: Map<String, XesExtensionAttribute> = Collections.unmodifiableMap(logInternal)

    /**
     * Captures the trace extension defnition from the XES metadata structure.
     */
    val trace: Map<String, XesExtensionAttribute> = Collections.unmodifiableMap(traceInternal)

    /**
     * Captures the event defnition from the XES metadata structure.
     */
    val event: Map<String, XesExtensionAttribute> = Collections.unmodifiableMap(eventInternal)

    /**
     * Captures the meta (attribute) extension defnition from the XES metadata structure.
     */
    val meta: Map<String, XesExtensionAttribute> = Collections.unmodifiableMap(metaInternal)
}