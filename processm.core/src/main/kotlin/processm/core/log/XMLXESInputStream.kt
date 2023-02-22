package processm.core.log

import processm.core.helpers.HashMapWithDefault
import processm.core.helpers.fastParseISO8601
import processm.core.helpers.toUUID
import processm.core.log.attribute.AttributeMap.Companion.LIST_TAG
import processm.core.log.attribute.MutableAttributeMap
import processm.core.logging.logger
import java.io.InputStream
import java.text.NumberFormat
import java.util.*
import javax.xml.stream.XMLInputFactory
import javax.xml.stream.XMLStreamReader

/**
 * Extracts a sequence of [XESComponent]s from the underlying stream.
 * @see XESInputStream
 */
class XMLXESInputStream(private val input: InputStream) : XESInputStream {
    companion object {
        private val exitTags = setOf("trace", "event")
        private val attributeTags = setOf("string", "date", "boolean", "int", "float", "list", "id")
    }

    /**
     * Number formatter for parsing numeric attributes. This object changes its state during parsing and cannot be
     * shared by the [XMLXESInputStream] instances.
     */
    private val numberFormatter = NumberFormat.getInstance(Locale.ROOT)
    private var lastSeenElement: String? = null


    /**
     * Maps standard names of attributes into custom names in the XES document being read.
     */
    private val nameMap = HashMapWithDefault<String, String>(false) { k -> k }

    override fun iterator(): Iterator<XESComponent> = sequence<XESComponent> {
        val xmlInputFactory: XMLInputFactory = XMLInputFactory.newDefaultFactory()
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

    private fun parseLog(reader: XMLStreamReader): Log = Log().also {
        it.xesVersion = reader.getAttributeValue(null, "xes.version")
        it.xesFeatures = reader.getAttributeValue(null, "xes.features")

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
                        parseAttributeTags(reader, reader.localName, it.attributesInternal)
                    }

                    else -> {
                        throw IllegalArgumentException("Found unexpected XML tag: ${reader.localName} in line ${reader.location.lineNumber} column ${reader.location.columnNumber}")
                    }
                }
            }
        }

        it.setStandardAttributes(nameMap)
    }

    private fun parseTrace(reader: XMLStreamReader) = Trace().also {
        lastSeenElement = null
        parseTraceOrEventTag(reader, it)
        it.setStandardAttributes(nameMap)
    }

    private fun parseEvent(reader: XMLStreamReader) = Event().also {
        lastSeenElement = null
        parseTraceOrEventTag(reader, it)
        it.setStandardAttributes(nameMap)
    }

    private fun addExtensionToLogElement(log: Log, reader: XMLStreamReader) {
        val prefix = reader.getAttributeValue(null, "prefix")
        val extension = Extension(
            reader.getAttributeValue(null, "name"),
            prefix,
            reader.getAttributeValue(null, "uri")
        )

        // map standard attribute names to custom names in the log
        if (extension.extension != null)
            extension.mapStandardToCustomNames(this.nameMap)

        log.extensionsInternal[prefix] = extension
    }

    private fun addClassifierToLogElement(log: Log, reader: XMLStreamReader) {
        val classifiers = when (reader.getAttributeValue(null, "scope") ?: "event") {
            "trace" -> log.traceClassifiersInternal
            "event" -> log.eventClassifiersInternal
            else -> throw IllegalArgumentException(
                "Illegal <classifier> scope. Expected 'trace' or 'event', found ${
                    reader.getAttributeValue(null, "scope")
                } in line ${reader.location.lineNumber} column ${reader.location.columnNumber}"
            )
        }

        val name = reader.getAttributeValue(null, "name")
        val classifier = Classifier(name, reader.getAttributeValue(null, "keys"))

        classifiers[name] = classifier
    }

    private fun addGlobalAttributes(map: MutableAttributeMap, reader: XMLStreamReader) {
        while (reader.hasNext()) {
            reader.next()

            if (reader.isStartElement) {
                parseAttributeTags(reader, reader.localName, map)
            } else if (reader.isEndElement && reader.localName == "global") {
                break
            }
        }
    }

    private fun parseAttributeTags(
        reader: XMLStreamReader,
        elementName: String,
        parentStorage: MutableAttributeMap
    ) {
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
        val attribute = castToAttribute(reader.localName, key, value, parentStorage)

        while (reader.hasNext()) {
            reader.next()

            if (reader.isEndElement && reader.localName == elementName) {
                break
            } else if (reader.isStartElement) {
                if (reader.localName == "values") {
                    var ctr = 0
                    val list = parentStorage.children(key)
                    while (reader.hasNext()) {
                        reader.next()

                        if (reader.isStartElement) {
                            parseAttributeTags(reader, reader.localName, list.children(ctr++))
                        } else if (reader.isEndElement) {
                            assert(reader.localName == "values")
                            break
                        }
                    }
                } else {
                    parseAttributeTags(reader, reader.localName, parentStorage.children(key))
                }
            }
        }
    }

    private fun parseTraceOrEventTag(reader: XMLStreamReader, xesComponent: XESComponent) {
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
                        parseAttributeTags(reader, reader.localName, xesComponent.attributesInternal)
                    }

                    else -> {
                        throw IllegalArgumentException("Found unexpected XML tag: ${reader.localName} in line ${reader.location.lineNumber} column ${reader.location.columnNumber}")
                    }
                }
            } else if (reader.isEndElement && reader.localName in exitTags) {
                lastSeenElement = reader.localName
                break
            }
        }
    }

    private fun castToAttribute(
        type: String,
        key: String,
        value: String,
        storage: MutableAttributeMap
    ) {
        when (type) {
            "string" -> storage[key] = value
            "float" -> storage[key] = numberFormatter.parse(value).toDouble()
            "id" -> storage[key] = requireNotNull(value.toUUID())
            "int" -> storage[key] = value.toLong()
            "date" -> storage[key] = value.fastParseISO8601()
            "boolean" -> storage[key] = value.toBoolean()
            "list" -> storage[key] = LIST_TAG

            else -> throw IllegalArgumentException("Attribute not recognized. Received $type type.")
        }
    }
}
