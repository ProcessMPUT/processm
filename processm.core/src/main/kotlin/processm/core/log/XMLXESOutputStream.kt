package processm.core.log

import processm.core.Brand
import processm.core.log.attribute.Attribute
import processm.core.log.attribute.ListAttr
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import javax.xml.stream.XMLStreamWriter

class XMLXESOutputStream(private val output: XMLStreamWriter) : XESOutputStream {
    private var alreadyClosed: Boolean = false
    private var seenTag: String? = null

    /**
     * Write XESElement in XML output stream
     */
    override fun write(element: XESElement) {
        when (element) {
            is Event -> writeEvent(element)
            is Trace -> writeTrace(element)
            is Log -> writeLog(element)
            else -> throw IllegalArgumentException("Unexpected object ${element.javaClass}")
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
        startDocument()

        seenTag = "log"
        output.writeStartElement("log")
        output.writeAttribute("xes.version", "1.0")
        output.writeAttribute("xmlns", "http://www.xes-standard.org/")
        if (log.features?.isNotBlank() == true)
            output.writeAttribute("xes.features", log.features)

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
    private fun writeAttribute(attribute: Attribute<*>) {
        if (attribute.children.isEmpty()) {
            output.writeEmptyElement(attribute.xesTag)
            output.writeAttribute("key", attribute.key)
            if (attribute.xesTag != "list") {
                output.writeAttribute("value", attribute.valueToString())
            } else {
                assert(attribute is ListAttr)
                writeListAttributes((attribute as ListAttr).value)
            }

        } else {
            output.writeStartElement(attribute.xesTag)
            output.writeAttribute("key", attribute.key)
            if (attribute.xesTag != "list") {
                output.writeAttribute("value", attribute.valueToString())
                writeAttributes(attribute.children)
            } else {
                assert(attribute is ListAttr)
                writeAttributes(attribute.children)
                writeListAttributes((attribute as ListAttr).value)
            }

            output.writeEndElement()
        }
    }

    /**
     * Store ordered list of attributes in <values></values> tag if not empty list of children
     *
     * Used to store attributes in List<Attribute<*>>
     */
    private fun writeListAttributes(children: List<Attribute<*>>) {
        if (children.isNotEmpty()) {
            output.writeStartElement("values")

            for (attribute in children) {
                writeAttribute(attribute)
            }

            output.writeEndElement()
        }
    }

    /**
     * Store attributes
     */
    private fun writeAttributes(attributes: Map<String, Attribute<*>>) {
        for (attribute in attributes.values) {
            writeAttribute(attribute)
        }
    }

    /**
     * Write global's attributes in selected scope into XML file
     */
    private fun writeGlobals(scope: String, globals: Map<String, Attribute<*>>) {
        if (globals.isNotEmpty()) {
            output.writeStartElement("global")
            output.writeAttribute("scope", scope)

            for (global in globals.values) {
                writeAttribute(global)
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
    private fun startDocument() {
        val df = DateTimeFormatter.ISO_DATE_TIME.withZone(ZoneOffset.UTC)
        output.writeStartDocument("UTF-8", "1.0")
        output.writeComment("This file has been generated by ${Brand.name} software.")
        output.writeComment("XES standard version: 1.0")
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