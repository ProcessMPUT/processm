package processm.core.log

import processm.core.Brand
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import javax.xml.stream.XMLStreamWriter

/**
 * The output stream of XES components that formats a valid XES XML and JSON documents.
 * @property output The output XML or JSON stream writer. StAX and StAXON writers are supported.
 * @property sortAttributesByType Determines whether the attributes of log/trace/event are sorted by type. It is
 * recommended mode for StAXON writer, as it reduces the size of the resulting JSON and simplifies parsing. Setting
 * this property to true causes a little memory and time overhead when writing the stream.
 */
class XMLXESOutputStream(
    private val output: XMLStreamWriter,
    private val sortAttributesByType: Boolean = false
) : XESOutputStream {
    private var alreadyClosed: Boolean = false
    private var seenTag: String? = null

    /**
     * Write XESComponent in XML output stream
     */
    override fun write(component: XESComponent) {
        when (component) {
            is Event -> writeEvent(component)
            is Trace -> writeTrace(component)
            is Log -> writeLog(component)
            else -> throw IllegalArgumentException("Unexpected object ${component.javaClass}")
        }
    }

    /**
     * Close document and stream
     */
    override fun close() {
        if (!alreadyClosed) {
            endDocument()
            alreadyClosed = true

            output.close()
        }
    }

    /**
     * Close stream without ending document
     *
     * You should manipulate stream and ignore already generated content - can be invalid.
     */
    override fun abort() {
        alreadyClosed = true
        output.close()
    }

    /**
     * Write log tag with all fields from Log structure (globals, classifiers, extensions, attributes)
     */
    private fun writeLog(log: Log) {
        startDocument(log)

        seenTag = "log"
        output.writeStartElement("log")
        output.writeAttribute("xes.version", log.xesVersion ?: "1.0")
        output.writeAttribute("xmlns", "http://www.xes-standard.org/")
        if (log.xesFeatures?.isNotBlank() == true)
            output.writeAttribute("xes.features", log.xesFeatures)

        writeExtensions(log.extensions)
        writeGlobals("trace", log.traceGlobals)
        writeGlobals("event", log.eventGlobals)
        writeClassifiers("trace", log.traceClassifiers)
        writeClassifiers("event", log.eventClassifiers)
        writeAttributes(log.attributes)
    }

    /**
     * Write trace as <trace> tag
     * Close previously added trace tag if as last seen tag set 'event'
     *
     * Ignore trace tag when passed trace as event stream special trace
     */
    private fun writeTrace(trace: Trace) {
        // Ignore event stream trace
        if (trace.isEventStream) return

        // Close previous trace
        if (seenTag == "event") {
            output.writeEndElement()
        }

        seenTag = "trace"

        output.writeStartElement("trace")
        writeAttributes(trace.attributes)
    }

    /**
     * Write <event> tag with attributes and closing </event> tag
     */
    private fun writeEvent(event: Event) {
        seenTag = "event"

        output.writeStartElement("event")
        writeAttributes(event.attributes)
        output.writeEndElement()
    }

    /**
     * Store attribute as <xml-tag key value>
     * If ListAttr - ignore value and store ordered list of attributes (from value field)
     */
    private fun writeAttribute(key: String, value: Any?, children: AttributeMap) {
        if (children.isEmpty()) {
            output.writeEmptyElement(value.xesTag)
            output.writeAttribute("key", key)
            if (value.xesTag != "list") {
                output.writeAttribute("value", value.valueToString())
            } else {
                assert(value is List<*>)
                writeListAttributes(value as List<AttributeMap>)
            }

        } else {
            output.writeStartElement(value.xesTag)
            output.writeAttribute("key", key)
            if (value.xesTag != "list") {
                output.writeAttribute("value", value.valueToString())
                writeAttributes(children)
            } else {
                assert(value is List<*>)
                writeAttributes(children)
                writeListAttributes(value as List<AttributeMap>)
            }

            output.writeEndElement()
        }
    }

    /**
     * Store ordered list of attributes in <values></values> tag if not empty list of children
     *
     * Used to store attributes in List<Attribute<*>>
     */
    private fun writeListAttributes(children: List<AttributeMap>) {
        if (children.isNotEmpty()) {
            output.writeStartElement("values")

            for (attribute in children) {
                writeAttributes(attribute)
            }

            output.writeEndElement()
        }
    }

    /**
     * Store attributes
     */
    private fun writeAttributes(attributes: AttributeMap) {
        for ((key, value) in attributes.entries.let { if (sortAttributesByType) it.sortedBy { (k, _) -> k.xesTag } else it })
            writeAttribute(key, value, attributes.children(key))
    }

    /**
     * Write global's attributes in selected scope into XML file
     */
    private fun writeGlobals(scope: String, globals: AttributeMap) {
        if (globals.isNotEmpty()) {
            output.writeStartElement("global")
            output.writeAttribute("scope", scope)

            for ((key, global) in globals.entries) {
                writeAttribute(key, global, globals.children(key))
            }

            output.writeEndElement()
        }
    }

    /**
     * Store classifiers in XML
     */
    private fun writeClassifiers(scope: String, classifiers: Map<String, Classifier>) {
        for (classifier in classifiers.values) {
            output.writeEmptyElement("classifier")

            output.writeAttribute("name", classifier.name)
            output.writeAttribute("scope", scope)
            output.writeAttribute("keys", classifier.keys)
        }
    }

    /**
     * Extensions into XML
     */
    private fun writeExtensions(extensions: Map<String, Extension>) {
        for (extension in extensions.values) {
            output.writeEmptyElement("extension")

            output.writeAttribute("name", extension.name)
            output.writeAttribute("prefix", extension.prefix)
            output.writeAttribute("uri", extension.uri)
        }
    }

    /**
     * Start document
     * Set encoding, XML version and comments with brand's name, XES version and date of creation.
     */
    private fun startDocument(log: Log) {
        val df = DateTimeFormatter.ISO_DATE_TIME.withZone(ZoneOffset.UTC)
        output.writeStartDocument("UTF-8", "1.0")
        output.writeComment("This file has been generated by ${Brand.name} software.")
        output.writeComment("XES standard version: ${log.xesVersion ?: "1.0"}")
        output.writeComment("Generated: ${df.format(ZonedDateTime.now())}")
    }

    /**
     * Close last tag and whole document
     */
    private fun endDocument() {
        if (seenTag != null) {
            output.writeEndElement()

            output.writeEndDocument()
        }
    }
}
