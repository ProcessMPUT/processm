package processm.etl

import kotlinx.serialization.json.*
import processm.core.log.Event
import processm.core.log.Log
import processm.core.log.Trace
import processm.core.log.XESComponent
import processm.core.log.attribute.AttributeMap
import processm.core.log.attribute.MutableAttributeMap
import processm.core.log.attribute.valueToString
import java.time.Instant
import java.util.*

private fun JsonObjectBuilder.put(item: Map.Entry<String, Any?>) {
    //TODO support for lists and nested attributes
    val v = item.value
    put(
        item.key, when (v) {
            is String -> JsonPrimitive(v)
            is Long -> JsonPrimitive(v)
            is Int -> JsonPrimitive(v)
            is Double -> JsonPrimitive(v)
            is Instant -> JsonPrimitive(v.valueToString())
            is UUID -> JsonPrimitive(v.toString())
            is Boolean -> JsonPrimitive(v)
            null -> JsonNull
            else -> throw IllegalArgumentException("Unsupported type ${v::class}")
        }
    )
}

private fun AttributeMap.toJsonElement(additionalAttributes: AttributeMap? = null): JsonObject =
    buildJsonObject {
        this@toJsonElement.forEach { put(it) }
        additionalAttributes?.forEach { put(it) }
    }

/**
 * Crude and incomplete serialization of [XESComponent] as a [JsonObject]. The result resembles XES XML.
 *
 * @param additionalAttributes Additional attributes to be serialized as the top-level attributes of the object
 */
fun XESComponent.toJsonElement(additionalAttributes: AttributeMap? = null): JsonObject =
    buildJsonObject {
        put(
            "type", when (this@toJsonElement) {
                is Event -> "event"
                is Trace -> "trace"
                is Log -> "log"
                else -> throw IllegalArgumentException()
            }
        )
        put("attributes", attributes.toJsonElement(additionalAttributes))
        if (this@toJsonElement is Log) {
            put("trace_globals", traceGlobals.toJsonElement())
            put("event_globals", eventGlobals.toJsonElement())
            // TODO classifiers and extensions
        }
    }

private fun Iterator<XESComponent>.toJsonElements(
    logIdKey: String? = "log_id",
    traceIdKey: String? = "trace_id",
    eventIdKey: String? = "event_id"
): Iterator<JsonObject> = object : Iterator<JsonObject> {
    private var logId = 1L
    private var traceId = 1L
    private var eventId = 1L
    private val backend = TreeMap<String, Any?>()
    private val attrs = MutableAttributeMap(backend)

    override fun hasNext(): Boolean = this@toJsonElements.hasNext()

    override fun next(): JsonObject {
        val component = this@toJsonElements.next()
        backend.clear()
        logIdKey?.let { attrs[it] = logId }
        if (component is Log) {
            logId++
        } else {
            traceIdKey?.let { attrs[it] = traceId }
            if (component is Trace) {
                traceId++
            } else {
                eventIdKey?.let { attrs[eventIdKey] = eventId++ }
            }
        }
        return component.toJsonElement(attrs)
    }
}

/**
 * Transforms the given sequence of [XESComponent]s into a sequence of [JsonObject]s using many naive assumptions:
 * * Attributes are mapped into the JSON object `attributes` such that the attribute's name becomes the key, and the
 *   attribute's value - value. The type information is not maintained.
 * * Complex attributes (nested, lists) are not supported
 * * Logs, traces and events are assigned sequential IDs (starting from 1), and they are represented by the attributes
 *   [logIdKey], [traceIdKey] and [eventIdKey] respectively.
 * * Log classifiers and extensions are not maintained.
 *
 * If [logIdKey] (resp. [traceIdKey], [eventIdKey]) is null, the corresponding attribute is not inserted into the final object.
 */
fun Sequence<XESComponent>.toJsonElements(
    logIdKey: String? = "log_id",
    traceIdKey: String? = "trace_id",
    eventIdKey: String? = "event_id"
): Sequence<JsonObject> = object : Sequence<JsonObject> {

    override fun iterator(): Iterator<JsonObject> =
        this@toJsonElements.iterator().toJsonElements(logIdKey, traceIdKey, eventIdKey)
}