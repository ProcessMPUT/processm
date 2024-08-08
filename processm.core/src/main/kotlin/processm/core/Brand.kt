package processm.core

import java.util.*

/**
 * An object for customization of branding information for ProcessM. All user-interface strings using the branding
 * information should refer the properties of this object.
 */
object Brand {
    private val properties = Properties().apply {
        load(Brand::class.java.classLoader.getResourceAsStream("brand.properties"))
    }
    val name: String = properties.getProperty("processm.brand")
    val version: String = properties.getProperty("processm.version")
    const val mainDBInternalName: String = "processm"
}
