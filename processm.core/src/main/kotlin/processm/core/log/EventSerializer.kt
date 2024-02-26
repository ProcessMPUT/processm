package processm.core.log

import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.*
import processm.core.log.attribute.MutableAttributeMap
import processm.helpers.fastParseISO8601
import java.time.format.DateTimeParseException

/**
 * A serializer that maps event attributes into fields in the json object and vice-versa.
 */
object EventSerializer : KSerializer<Event> {
    override val descriptor: SerialDescriptor
        get() = PrimitiveSerialDescriptor("Event", PrimitiveKind.STRING)

    override fun deserialize(decoder: Decoder): Event {
        val json = decoder.decodeSerializableValue(JsonObject.serializer())
        val attributes = MutableAttributeMap()
        for ((k, v) in json) {
            if (v is JsonNull) {
                attributes.putFlat(k, null)
            } else {
                val primitive = v.jsonPrimitive
                if (primitive.isString) {
                    try {
                        attributes.putFlat(k, primitive.content.fastParseISO8601())
                    } catch (e: DateTimeParseException) {
                        attributes.putFlat(k, primitive.content)
                    }
                } else {
                    // Unfortunately JsonPrimitive does not provide type information, so we try converting the value.
                    // Order is important: putFlat executes only if the value parses to the correct type.
                    // A number with point is parsable to double/float only, and number without point is parsable to
                    // floating point and integer numeric types.
                    primitive.longOrNull?.let { attributes.putFlat(k, it) }
                    primitive.doubleOrNull?.let { attributes.putFlat(k, it) }
                    primitive.booleanOrNull?.let { attributes.putFlat(k, it) }
                }
            }
        }
        return Event(attributes)
    }

    override fun serialize(encoder: Encoder, value: Event) {
        val json = JsonObject(value.attributes.flatView.mapValues { (_, v) ->
            when (v) {
                is String -> JsonPrimitive(v)
                is Number -> JsonPrimitive(v) // includes Float, Double, Int, Long
                is Boolean -> JsonPrimitive(v)
                null -> JsonNull
                else -> JsonPrimitive(v.toString()) // includes Instant
            }
        })
        encoder.encodeSerializableValue(JsonObject.serializer(), json)
    }
}
