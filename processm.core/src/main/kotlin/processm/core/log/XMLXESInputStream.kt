package processm.core.log

import processm.core.helpers.fastParseISO8601
import processm.core.log.attribute.*
import processm.core.logging.logger
import java.io.InputStream
import java.text.NumberFormat
import java.time.Instant
import java.util.*
import javax.xml.stream.XMLInputFactory
import javax.xml.stream.XMLStreamReader

/**
 * Extracts a sequence of [XESElement]s from the underlying stream.
 * @see XESInputStream
 */
class XMLXESInputStream(private val input: InputStream) : XESInputStream {
    companion object {
        private val exitTags = setOf("trace", "event")
        private val attributeTags = setOf("string", "date", "boolean", "int", "float", "list", "id")
        private val numberFormatter = NumberFormat.getInstance(Locale.ROOT)
        private var lastSeenElement: String? = null
    }

    /**
     * Maps standard names of attributes into custom names in the XES document being read.
     */
    private val nameMap = object : HashMap<String, String>() {
        override fun get(key: String): String? = super.get(key) ?: key
    }

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
                    else -> throw IllegalArgumentException("Found unexpected XML tag: ${reader.localName} in line ${reader.location.lineNumber} column ${reader.location.columnNumber}")
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
            // this.nameMap[extension.extension.uri] = prefix
            for (name in extension.extension.log.keys)
                this.nameMap["${extension.extension.prefix}:$name"] = "$prefix:$name"
            for (name in extension.extension.trace.keys)
                this.nameMap["${extension.extension.prefix}:$name"] = "$prefix:$name"
            for (name in extension.extension.event.keys)
                this.nameMap["${extension.extension.prefix}:$name"] = "$prefix:$name"
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
        log.conceptName = log.attributes[nameMap["concept:name"]]?.getValue() as String?
        log.identityId = log.attributes[nameMap["identity:id"]]?.getValue() as String?
        log.lifecycleModel = log.attributes[nameMap["lifecycle:model"]]?.getValue() as String?
    }

    private fun addGeneralMeaningFieldsIntoTrace(trace: Trace) {
        trace.conceptName = trace.attributes[nameMap["concept:name"]]?.getValue() as String?

        trace.costTotal = trace.attributes[nameMap["cost:total"]]?.getValue() as Double?
        trace.costCurrency = trace.attributes[nameMap["cost:currency"]]?.getValue() as String?

        trace.identityId = trace.attributes[nameMap["identity:id"]]?.getValue() as String?
    }

    private fun addGeneralMeaningFieldsIntoEvent(event: Event) {
        event.conceptName = event.attributes[nameMap["concept:name"]]?.getValue() as String?
        event.conceptInstance = event.attributes[nameMap["concept:instance"]]?.getValue() as String?

        event.costTotal = event.attributes[nameMap["cost:total"]]?.getValue() as Double?
        event.costCurrency = event.attributes[nameMap["cost:currency"]]?.getValue() as String?

        event.identityId = event.attributes[nameMap["identity:id"]]?.getValue() as String?

        event.lifecycleState = event.attributes[nameMap["lifecycle:state"]]?.getValue() as String?
        event.lifecycleTransition =
            event.attributes[nameMap["lifecycle:transition"]]?.getValue() as String?

        event.orgRole = event.attributes[nameMap["org:role"]]?.getValue() as String?
        event.orgGroup = event.attributes[nameMap["org:group"]]?.getValue() as String?
        event.orgResource = event.attributes[nameMap["org:resource"]]?.getValue() as String?

        event.timeTimestamp = event.attributes[nameMap["time:timestamp"]]?.getValue() as Instant?
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
                DateTimeAttr(key, value.fastParseISO8601())
            }
            else ->
                throw Exception("Attribute not recognized. Received $type type.")
        }
    }
}