package processm.core.log

import kotlinx.io.InputStream
import processm.core.log.attribute.*
import processm.core.logging.logger
import java.text.NumberFormat
import java.time.Instant
import java.time.format.DateTimeFormatter
import java.util.*
import javax.xml.namespace.QName
import javax.xml.stream.XMLEventReader
import javax.xml.stream.XMLInputFactory
import javax.xml.stream.events.StartElement
import javax.xml.stream.events.XMLEvent
import kotlin.collections.HashMap

/**
 * Extracts a sequence of [XESElement]s from the underlying stream.
 * @see XESInputStream
 */
class XMLXESInputStream(private val input: InputStream) : XESInputStream {
    companion object {
        private val exitTags = setOf("trace", "event")
        private val attributeTags = setOf("string", "date", "boolean", "int", "float", "list", "id")
        private val numberFormatter = NumberFormat.getInstance(Locale.ROOT)
        private val dateFormatter = DateTimeFormatter.ISO_DATE_TIME
    }

    private val prefixesMapping: HashMap<String, String> = HashMap()

    override fun iterator(): Iterator<XESElement> = sequence<XESElement> {
        val xmlInputFactory: XMLInputFactory = XMLInputFactory.newInstance()
        val reader: XMLEventReader = xmlInputFactory.createXMLEventReader(input)

        while (reader.hasNext()) {
            val nextEvent: XMLEvent = reader.nextEvent()

            if (nextEvent.isStartElement) {
                val element: StartElement = nextEvent.asStartElement()
                val xesElement = when (element.name.localPart) {
                    "log" -> parseLog(reader, element)
                    "trace" -> parseTrace(reader)
                    "event" -> parseEvent(reader)
                    else -> throw Exception("Found unexpected XML tag: ${element.name.localPart}in line ${element.location.lineNumber} column ${element.location.columnNumber}")
                }
                yield(xesElement)
            }
        }
    }.iterator()

    private fun parseLog(reader: XMLEventReader, _element: StartElement) = Log().also {
        var element = _element
        it.features = element.getAttributeByName(QName("xes.features"))?.value

        // Read until have next and do not find 'trace' or 'event' element
        while (reader.hasNext()) {
            val event = reader.peek()

            if (event.isStartElement && (event.asStartElement().name.localPart in exitTags)) {
                break
            }

            reader.nextEvent()
            if (event.isStartElement) {
                element = event.asStartElement()
                when (element.name.localPart) {
                    "extension" ->
                        addExtensionToLogElement(it, element)
                    "classifier" ->
                        addClassifierToLogElement(it, element)
                    "global" -> {
                        // Based on 5.6.2 Attributes IEEE Standard for eXtensible Event Stream (XES) for Achieving Interoperability in Event Logs and Event Streams
                        // Scope is optional, default 'event'

                        val map =
                            when (val scope =
                                element.getAttributeByName(QName("scope"))?.value ?: "event") {
                                "trace" -> it.traceGlobalsInternal
                                "event" -> it.eventGlobalsInternal
                                else -> throw Exception("Illegal <global> scope. Expected 'trace' or 'event', found $scope in line ${element.location.lineNumber} column ${element.location.columnNumber}")
                            }

                        addGlobalAttributes(map, reader)
                    }
                    in attributeTags -> {
                        with(parseAttributeTags(element, reader)) {
                            it.attributesInternal[this.key] = this
                        }
                    }
                    else -> {
                        throw Exception("Found unexpected XML tag: ${element.name.localPart} in line ${element.location.lineNumber} column ${element.location.columnNumber}")
                    }
                }
            }
        }

        addGeneralMeaningFieldsIntoLog(it)
    }

    private fun parseTrace(reader: XMLEventReader) = Trace().also {
        parseTraceOrEventTag(reader, it)
        addGeneralMeaningFieldsIntoTrace(it)
    }

    private fun parseEvent(reader: XMLEventReader) = Event().also {
        parseTraceOrEventTag(reader, it)
        addGeneralMeaningFieldsIntoEvent(it)
    }

    private fun addExtensionToLogElement(log: Log, extensionElement: StartElement) {
        val prefix = extensionElement.getAttributeByName(QName("prefix")).value
        val extension = Extension(
            extensionElement.getAttributeByName(QName("name")).value,
            prefix,
            extensionElement.getAttributeByName(QName("uri")).value
        )

        // Add mapping extension's URI -> user's prefix
        if (extension.extension != null) {
            this.prefixesMapping[extension.extension.uri] = prefix
        }

        log.extensionsInternal[prefix] = extension
    }

    private fun addClassifierToLogElement(log: Log, classifierElement: StartElement) {
        val classifiers = when (val scope = classifierElement.getAttributeByName(QName("scope"))?.value ?: "event") {
            "trace" -> log.traceClassifiersInternal
            "event" -> log.eventClassifiersInternal
            else -> throw Exception("Illegal <classifier> scope. Expected 'trace' or 'event', found $scope in line ${classifierElement.location.lineNumber} column ${classifierElement.location.columnNumber}")
        }

        val name = classifierElement.getAttributeByName(QName("name")).value
        val classifier = Classifier(name, classifierElement.getAttributeByName(QName("keys")).value)

        classifiers[name] = classifier
    }

    private fun addGlobalAttributes(map: MutableMap<String, Attribute<*>>, reader: XMLEventReader) {
        while (reader.hasNext()) {
            val event = reader.nextEvent()

            if (event.isStartElement) {
                val element = event.asStartElement()
                with(parseAttributeTags(element, reader)) {
                    map[this.key] = this
                }
            } else if (event.isEndElement && event.asEndElement().name.localPart == "global") {
                break
            }
        }
    }

    private fun parseAttributeTags(element: StartElement, reader: XMLEventReader): Attribute<*> {
        val key = with(element.getAttributeByName(QName("key"))?.value) {
            if (this == null) {
                logger().warn("Missing key in XES log file in line ${element.location.lineNumber} column ${element.location.columnNumber}")
                ""
            } else this
        }
        val value = with(element.getAttributeByName(QName("value"))?.value) {
            if (this == null) {
                if (element.name.localPart != "list")
                    logger().warn("Missing value in XES log file in line ${element.location.lineNumber} column ${element.location.columnNumber}")

                ""
            } else this
        }
        val attribute = castToAttribute(element.name.localPart, key, value)

        while (reader.hasNext()) {
            var event = reader.nextEvent()

            if (event.isEndElement) {
                if (event.asEndElement().name.localPart == element.name.localPart) {
                    break
                }
            } else if (event.isStartElement) {
                var nextAttributeElement = event.asStartElement()
                if (nextAttributeElement.name.localPart == "values") {
                    while (reader.hasNext()) {
                        event = reader.nextEvent()

                        if (event.isStartElement) {
                            nextAttributeElement = event.asStartElement()
                            with(parseAttributeTags(nextAttributeElement, reader)) {
                                (attribute as ListAttr).valueInternal.add(this)
                            }
                        } else if (event.isEndElement) {
                            assert(event.asEndElement().name.localPart == "values")
                            break
                        }
                    }
                } else {
                    with(parseAttributeTags(nextAttributeElement, reader)) {
                        attribute.childrenInternal[this.key] = this
                    }
                }
            }
        }

        return attribute
    }

    private fun addGeneralMeaningFieldsIntoLog(log: Log) {
        val conceptPrefix = this.prefixesMapping[XESExtensionLoader.concept.uri]
        if (conceptPrefix != null)
            log.conceptName = log.attributes["$conceptPrefix:name"]?.getValue() as String?

        val identityPrefix = this.prefixesMapping[XESExtensionLoader.identity.uri]
        if (identityPrefix != null)
            log.identityId = log.attributes["$identityPrefix:id"]?.getValue() as String?

        val lifecyclePrefix = this.prefixesMapping[XESExtensionLoader.lifecycle.uri]
        if (lifecyclePrefix != null)
            log.lifecycleModel = log.attributes["$lifecyclePrefix:model"]?.getValue() as String?
    }

    private fun addGeneralMeaningFieldsIntoTrace(trace: Trace) {
        val conceptPrefix = this.prefixesMapping[XESExtensionLoader.concept.uri]
        if (conceptPrefix != null)
            trace.conceptName = trace.attributes["$conceptPrefix:name"]?.getValue() as String?

        val costPrefix = this.prefixesMapping[XESExtensionLoader.cost.uri]
        if (costPrefix != null) {
            trace.costTotal = trace.attributes["$costPrefix:total"]?.getValue() as Double?
            trace.costCurrency = trace.attributes["$costPrefix:currency"]?.getValue() as String?
        }

        val identityPrefix = this.prefixesMapping[XESExtensionLoader.identity.uri]
        if (identityPrefix != null)
            trace.identityId = trace.attributes["$identityPrefix:id"]?.getValue() as String?
    }

    private fun addGeneralMeaningFieldsIntoEvent(event: Event) {
        val conceptPrefix = this.prefixesMapping[XESExtensionLoader.concept.uri]
        if (conceptPrefix != null) {
            event.conceptName = event.attributes["$conceptPrefix:name"]?.getValue() as String?
            event.conceptInstance = event.attributes["$conceptPrefix:instance"]?.getValue() as String?
        }

        val costPrefix = this.prefixesMapping[XESExtensionLoader.cost.uri]
        if (costPrefix != null) {
            event.costTotal = event.attributes["$costPrefix:total"]?.getValue() as Double?
            event.costCurrency = event.attributes["$costPrefix:currency"]?.getValue() as String?
        }

        val identityPrefix = this.prefixesMapping[XESExtensionLoader.identity.uri]
        if (identityPrefix != null)
            event.identityId = event.attributes["$identityPrefix:id"]?.getValue() as String?

        val lifecyclePrefix = this.prefixesMapping[XESExtensionLoader.lifecycle.uri]
        if (lifecyclePrefix != null) {
            event.lifecycleState = event.attributes["$lifecyclePrefix:state"]?.getValue() as String?
            event.lifecycleTransition = event.attributes["$lifecyclePrefix:transition"]?.getValue() as String?
        }

        val orgPrefix = this.prefixesMapping[XESExtensionLoader.org.uri]
        if (orgPrefix != null) {
            event.orgRole = event.attributes["$orgPrefix:role"]?.getValue() as String?
            event.orgGroup = event.attributes["$orgPrefix:group"]?.getValue() as String?
            event.orgResource = event.attributes["$orgPrefix:resource"]?.getValue() as String?
        }

        val timePrefix = this.prefixesMapping[XESExtensionLoader.time.uri]
        if (timePrefix != null) {
            event.timeTimestamp = event.attributes["$timePrefix:timestamp"]?.getValue() as Date?
        }
    }

    private fun parseTraceOrEventTag(reader: XMLEventReader, xesElement: XESElement) {
        // Read until has next and not found next 'event' or 'trace' element
        while (reader.hasNext()) {
            val event = reader.peek()

            if (event.isStartElement && (event.asStartElement().name.localPart in exitTags)) {
                break
            }

            reader.nextEvent()
            if (event.isStartElement) {
                val element = event.asStartElement()
                when (element.name.localPart) {
                    in attributeTags -> {
                        with(parseAttributeTags(element, reader)) {
                            xesElement.attributesInternal[this.key] = this
                        }
                    }
                    else -> {
                        throw Exception("Found unexpected XML tag: ${element.name.localPart} in line ${element.location.lineNumber} column ${element.location.columnNumber}")
                    }
                }
            }
        }
    }

    private fun castToAttribute(type: String, key: String, value: String): Attribute<*> {
        return when (type) {
            "float" ->
                RealAttr(key, numberFormatter.parse(value).toDouble())
            "string" ->
                StringAttr(key, value)
            "boolean" ->
                BoolAttr(key, value.toBoolean())
            "id" ->
                IDAttr(key, value)
            "int" ->
                IntAttr(key, value.toLong())
            "list" ->
                ListAttr(key)
            "date" -> {
                DateTimeAttr(key, Date.from(Instant.from(dateFormatter.parse(value))))
            }
            else ->
                throw Exception("Attribute not recognized. Received $type type.")
        }
    }
}