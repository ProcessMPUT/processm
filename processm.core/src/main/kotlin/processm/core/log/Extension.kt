package processm.core.log

import processm.core.log.extension.Extension

/**
 * XES Extension element
 */
class Extension(name: String?, prefix: String?, uri: String?) {
    /**
     * The name of the extension from XES log file.
     */
    val name: String? = name?.intern()
    /**
     * The prefix to be used for this extension from XES log file.
     */
    val prefix: String? = prefix?.intern()
    /**
     * The URI where this extension can be retrieved from
     */
    val uri: String? = uri?.intern()
    /**
     * Loaded extension
     */
    val extension: Extension? = if (uri.isNullOrEmpty()) null else XESExtensionLoader.loadExtension(uri)
}