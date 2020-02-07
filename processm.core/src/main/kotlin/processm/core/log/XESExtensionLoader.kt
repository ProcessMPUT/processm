package processm.core.log

import org.w3c.dom.Document
import org.w3c.dom.NodeList
import processm.core.log.extension.Extension
import processm.core.log.extension.XesExtensionAttribute
import processm.core.logging.logger
import java.io.InputStream
import java.net.URL
import java.util.concurrent.ConcurrentHashMap
import javax.xml.parsers.DocumentBuilderFactory

object XESExtensionLoader {
    private val loadedExtensions: ConcurrentHashMap<String, Extension> = ConcurrentHashMap()
    private val allowedAttributesNames = listOf("string", "float", "int", "id", "list", "date")
    private val xesWebsiteRegex: Regex =
        "^https?://(www.)?xes-standard.org/(?<ext>[a-z_]+).xesext\$".toRegex(RegexOption.IGNORE_CASE)

    internal fun loadExtension(uri: String): Extension? {
        try {
            // Return stored Extension if in memory
            val extensionInMemory = loadedExtensions[uri]
            if (extensionInMemory != null) {
                return extensionInMemory
            }

            // Stream to read from local resources OR from the Internet
            var stream: InputStream? = null

            // Try to load from local resources - open stream
            val extensionName = xesWebsiteRegex.matchEntire(uri)?.groups?.get("ext")?.value?.toLowerCase()
            if (!extensionName.isNullOrEmpty()) {
                stream = javaClass.classLoader.getResourceAsStream("xes-extensions/$extensionName.xesext")
            }

            // Try to load from the Internet
            if (stream == null) {
                stream = openExternalStream(uri)
            }

            // Store new extension in memory
            val extension = xmlToObject(stream)
            return loadedExtensions.putIfAbsent(uri, extension) ?: extension
        } catch (e: Exception) {
            logger().warn("Cannot load XES Extension", e)
        }

        return null
    }

    private fun openExternalStream(url: String): InputStream? {
        return URL(url).openStream()
    }

    private fun xmlToObject(stream: InputStream?): Extension {
        val xmlDoc: Document = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(stream)
        xmlDoc.documentElement.normalize()

        if (!xmlDoc.documentElement.nodeName.equals("xesextension", ignoreCase = true))
            throw IllegalArgumentException("Expected xesextension element, ${xmlDoc.documentElement.nodeName} found.")

        val extensionAttrs = xmlDoc.documentElement.attributes
        val extension: Extension = Extension(
            extensionAttrs.getNamedItem("name").nodeValue,
            extensionAttrs.getNamedItem("prefix").nodeValue,
            extensionAttrs.getNamedItem("uri").nodeValue
        )

        parseXESTag(xmlDoc, "log", extension.logInternal)
        parseXESTag(xmlDoc, "trace", extension.traceInternal)
        parseXESTag(xmlDoc, "event", extension.eventInternal)
        parseXESTag(xmlDoc, "meta", extension.metaInternal)

        return extension
    }

    private fun parseXESTag(
        xmlDoc: Document,
        elementName: String,
        extensionLanguageField: MutableMap<String, XesExtensionAttribute>
    ) {
        val traceList: NodeList = xmlDoc.getElementsByTagName(elementName)
        for (i in 0 until traceList.length) {
            var child = traceList.item(i).firstChild
            while (child != null) {
                if (child.nodeName in this.allowedAttributesNames) {
                    val fieldKey = child.attributes.getNamedItem("key").nodeValue.toString().intern()
                    val languageMapping = XesExtensionAttribute(fieldKey, child.nodeName)

                    // Find mapping with languages inside tag structure
                    var childInside = child.firstChild
                    while (childInside != null) {
                        // We want to open only 'alias' tags, ignore comments, empty lines etc.
                        if (childInside.nodeName.equals("alias", ignoreCase = true)) {
                            // Add new language to structure
                            languageMapping.mapping.putIfAbsent(
                                childInside.attributes.getNamedItem("mapping").nodeValue.intern(),
                                childInside.attributes.getNamedItem("name").nodeValue
                            )
                        }
                        childInside = childInside.nextSibling
                    }

                    // Add prepared structure with languages mapping into extension structure
                    extensionLanguageField[fieldKey] = languageMapping
                }
                child = child.nextSibling
            }
        }
    }
}