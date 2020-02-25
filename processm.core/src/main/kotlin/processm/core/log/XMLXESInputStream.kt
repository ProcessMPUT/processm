package processm.core.log

import kotlinx.io.InputStream
import processm.core.log.attribute.*
import processm.core.logging.logger
import java.text.NumberFormat
import java.time.Instant
import java.time.format.DateTimeFormatter
import java.util.*
import javax.xml.stream.XMLInputFactory
import javax.xml.stream.XMLStreamReader
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
        private var lastSeenElement: String? = null
    }

    private val prefixesMapping: HashMap<String, String> = HashMap()

    override fun iterator(): Iterator<XESElement> = sequence<XESElement> {
        val xmlInputFactory: XMLInputFactory = XMLInputFactory.newInstance()
        val reader = xmlInputFactory.createXMLStreamReader(input)

        while (reader.hasNext()) {
            if (!reader.isStartElement || lastSeenElement == null)
                reader.next()

            if (reader.isStartElement) {
                val xesElement = when (reader.localName) {
                    "log" -> parseLog(reader)
                    "trace" -> parseTrace(reader)
                    "event" -> parseEvent(reader)
                    else -> throw Exception("Found unexpected XML tag: ${reader.localName} in line ${reader.location.lineNumber} column ${reader.location.columnNumber}")
                }
                yield(xesElement)
            }
        }
    }.iterator()

    private fun parseLog(reader: XMLStreamReader) = Log().also {
        it.features = reader.getAttributeValue(null, "xes.features")

        // Read until have next and do not find 'trace' or 'event' element
        while (reader.hasNext()) {
            reader.next()

            if (reader.isStartElement) {
                if (reader.localName in exitTags) {
                    lastSeenElement = reader.localName
                    break
                }

                when (reader.localName) {
                    "extension" ->
                        addExtensionToLogElement(it, reader)
                    "classifier" ->
                        addClassifierToLogElement(it, reader)
                    "global" -> {
                        // Based on 5.6.2 Attributes IEEE Standard for eXtensible Event Stream (XES) for Achieving Interoperability in Event Logs and Event Streams
                        // Scope is optional, default 'event'

                        val map =
                            when (val scope = reader.getAttributeValue(null, "scope") ?: "event") {
                                "trace" -> it.traceGlobalsInternal
                                "event" -> it.eventGlobalsInternal
                                else -> throw Exception("Illegal <global> scope. Expected 'trace' or 'event', found $scope in line ${reader.location.lineNumber} column ${reader.location.columnNumber}")
                            }

                        addGlobalAttributes(map, reader)
                    }
                    in attributeTags -> {
                        with(parseAttributeTags(reader, reader.localName)) {
                            it.attributesInternal[this.key] = this
                        }
                    }
                    else -> {
                        throw Exception("Found unexpected XML tag: ${reader.localName} in line ${reader.location.lineNumber} column ${reader.location.columnNumber}")
                    }
                }
            }
        }

        addGeneralMeaningFieldsIntoLog(it)
    }

    private fun parseTrace(reader: XMLStreamReader) = Trace().also {
        lastSeenElement = null
        parseTraceOrEventTag(reader, it)
        addGeneralMeaningFieldsIntoTrace(it)
    }

    private fun parseEvent(reader: XMLStreamReader) = Event().also {
        lastSeenElement = null
        parseTraceOrEventTag(reader, it)
        addGeneralMeaningFieldsIntoEvent(it)
    }

    private fun addExtensionToLogElement(log: Log, reader: XMLStreamReader) {
        val prefix = reader.getAttributeValue(null, "prefix")
        val extension = Extension(
            reader.getAttributeValue(null, "name"),
            prefix,
            reader.getAttributeValue(null, "uri")
        )

        // Add mapping extension's URI -> user's prefix
        if (extension.extension != null) {
            this.prefixesMapping[extension.extension.uri] = prefix
        }

        log.extensionsInternal[prefix] = extension
    }

    private fun addClassifierToLogElement(log: Log, reader: XMLStreamReader) {
        val classifiers = when (reader.getAttributeValue(null, "scope") ?: "event") {
            "trace" -> log.traceClassifiersInternal
            "event" -> log.eventClassifiersInternal
            else -> throw Exception(
                "Illegal <classifier> scope. Expected 'trace' or 'event', found ${reader.getAttributeValue(
                    null,
                    "scope"
                )} in line ${reader.location.lineNumber} column ${reader.location.columnNumber}"
            )
        }

        val name = reader.getAttributeValue(null, "name")
        val classifier = Classifier(name, reader.getAttributeValue(null, "keys"))

        classifiers[name] = classifier
    }

    private fun addGlobalAttributes(map: MutableMap<String, Attribute<*>>, reader: XMLStreamReader) {
        while (reader.hasNext()) {
            reader.next()

            if (reader.isStartElement) {
                with(parseAttributeTags(reader, reader.localName)) {
                    map[this.key] = this
                }
            } else if (reader.isEndElement && reader.localName == "global") {
                break
            }
        }
    }

    private fun parseAttributeTags(reader: XMLStreamReader, elementName: String): Attribute<*> {
        val key = with(reader.getAttributeValue(null, "key")) {
            if (this == null) {
                logger().warn("Missing key in XES log file in line ${reader.location.lineNumber} column ${reader.location.columnNumber}")
                ""
            } else this
        }
        val value = with(reader.getAttributeValue(null, "value")) {
            if (this == null) {
                if (reader.localName != "list")
                    logger().warn("Missing value in XES log file in line ${reader.location.lineNumber} column ${reader.location.columnNumber}")

                ""
            } else this
        }
        val attribute = castToAttribute(reader.localName, key, value)

        while (reader.hasNext()) {
            reader.next()

            if (reader.isEndElement && reader.localName == elementName) {
                break
            } else if (reader.isStartElement) {
                if (reader.localName == "values") {
                    while (reader.hasNext()) {
                        reader.next()

                        if (reader.isStartElement) {
                            with(parseAttributeTags(reader, reader.localName)) {
                                (attribute as ListAttr).valueInternal.add(this)
                            }
                        } else if (reader.isEndElement) {
                            assert(reader.localName == "values")
                            break
                        }
                    }
                } else {
                    with(parseAttributeTags(reader, reader.localName)) {
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

    private fun parseTraceOrEventTag(reader: XMLStreamReader, xesElement: XESElement) {
        // Read until has next and not found next 'event' or 'trace' element
        while (reader.hasNext()) {
            reader.next()

            if (reader.isStartElement) {
                if (reader.localName in exitTags) {
                    lastSeenElement = reader.localName
                    break
                }

                when (reader.localName) {
                    in attributeTags -> {
                        with(parseAttributeTags(reader, reader.localName)) {
                            xesElement.attributesInternal[this.key] = this
                        }
                    }
                    else -> {
                        throw Exception("Found unexpected XML tag: ${reader.localName} in line ${reader.location.lineNumber} column ${reader.location.columnNumber}")
                    }
                }
            } else if (reader.isEndElement && reader.localName in exitTags) {
                lastSeenElement = reader.localName
                break
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