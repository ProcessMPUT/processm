package processm.core.log

import org.w3c.dom.Document
import org.w3c.dom.NodeList
import processm.core.log.extension.Extension
import processm.core.log.extension.XesExtensionAttribute
import processm.core.logging.logger
import java.io.File
import java.io.InputStream
import java.net.URL
import java.util.concurrent.ConcurrentHashMap
import javax.xml.parsers.DocumentBuilderFactory

object XESExtensionLoader {
    /**
     * Loaded extensions memory - concurrent map to prevent concurrent access to memory
     */
    private val loadedExtensions: ConcurrentHashMap<String, Extension> = ConcurrentHashMap()

    /**
     * Allowed tags in XML file which we are able to parse inside <log>, <trace>, <event>, <meta> tag.
     * Variable in object to reduce dynamic allocation list in each comparation.
     */
    private val allowedAttributesNames = listOf("string", "float", "int", "id", "list", "date")

    /**
     * xes-standard website regex - If URI match it, we can use resources as first (reduce HTTP transfers).
     */
    private val xesWebsiteRegex: Regex =
        "^https?://(www.)?xes-standard.org/(?<ext>[a-z_]+).xesext\$".toRegex(RegexOption.IGNORE_CASE)

    /**
     * Extension with 'org' namespace: Organizational extension
     *
     * The organizational extension is useful for domains, where events can be caused by human actors
     * who are somewhat part of an organizational structure. This extension specifies three attributes for events,
     * which identify the actor having caused the event, and his position in the organizational structure.
     */
    val org by lazy { loadExtension("http://www.xes-standard.org/org.xesext")!! }

    /**
     * Extension with 'cost' namespace: Cost extension
     */
    val cost by lazy { loadExtension("http://www.xes-standard.org/cost.xesext")!! }

    /**
     *  Extension with 'time' namespace: Time extension
     *
     * In almost all applications, the exact date and time at which events occur can be precisely recorded.
     * Storing this information is the purpose of the time extension. Recording a UTC time for events is important,
     * since this constitutes crucial information for many event log analysis techniques
     */
    val time by lazy { loadExtension("http://www.xes-standard.org/time.xesext")!! }

    /**
     *  Extension with 'concept' namespace: Concept extension
     *
     * The Concept extension defines, for all levels of the XES type hierarchy, an attribute which stores the generally
     * understood name of type hierarchy elements.
     */
    val concept by lazy { loadExtension("http://www.xes-standard.org/concept.xesext")!! }

    /**
     *  Extension with 'identity' namespace: ID extension
     *
     * The ID extension provides unique identifiers (UUIDs) for elements.
     */
    val identity by lazy { loadExtension("http://www.xes-standard.org/identity.xesext")!! }

    /**
     *  Extension with 'lifecycle' namespace: Lifecycle extension
     *
     * The Lifecycle extension specifies for events the lifecycle transition they represent in a transactional
     * model of their generating activity. This transactional model can be arbitrary.
     */
    val lifecycle by lazy { loadExtension("http://www.xes-standard.org/lifecycle.xesext")!! }

    /**
     * Load exception from the provided URI address.
     *
     * Use memory to return already loaded extension (singleton) or fetch, parse and add to memory.
     * Can read standard extensions (defined by xes-standard.org) from project's resources.
     */
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
                // The path traversal attack should not be possible here, as xesWebsiteRegex does not allow for
                // slashes and backslashes in the extension name but for security reasons we do the check below.
                require(File.separatorChar !in extensionName) { "Path traversal attack detected." }
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