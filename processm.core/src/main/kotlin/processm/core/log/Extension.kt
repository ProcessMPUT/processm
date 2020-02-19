package processm.core.log

import processm.core.log.extension.Extension

class Extension(name: String?, prefix: String?, uri: String?) {
    val name: String? = name?.intern()
    val prefix: String? = prefix?.intern()
    val uri: String? = uri?.intern()

    val extension: Extension? = if (uri.isNullOrEmpty()) null else XESExtensionLoader.loadExtension(uri)
}