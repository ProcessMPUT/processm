package processm.core.log

import kotlin.collections.HashMap
import processm.core.log.extension.Extension as LoadedExtension

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
    val extension: LoadedExtension? = if (uri.isNullOrEmpty()) null else XESExtensionLoader.loadExtension(uri)

    internal fun mapStandardToCustomNames(nameMap: MutableMap<String, String>) {
        val extDecl = this.extension ?: return
        for (name in extDecl.log.keys)
            nameMap["${extDecl.prefix}:$name"] = "${this.prefix}:$name"
        for (name in extDecl.trace.keys)
            nameMap["${extDecl.prefix}:$name"] = "${this.prefix}:$name"
        for (name in extDecl.event.keys)
            nameMap["${extDecl.prefix}:$name"] = "${this.prefix}:$name"
    }

    /**
     * Extensions are equal if both contain the same `name`, `prefix` and `uri`
     */
    override fun equals(other: Any?): Boolean {
        if (other === this) return true
        if (other !is Extension) return false
        return name == other.name && prefix == other.prefix && uri == other.uri
    }

    override fun hashCode(): Int {
        var result = name?.hashCode() ?: 0
        result = 31 * result + (prefix?.hashCode() ?: 0)
        result = 31 * result + (uri?.hashCode() ?: 0)
        return result
    }
}

internal fun Iterable<Extension>.getStandardToCustomNameMap(): Map<String, String> =
    HashMap<String, String>().apply {
        for (extension in this@getStandardToCustomNameMap)
            extension.mapStandardToCustomNames(this)
    }
