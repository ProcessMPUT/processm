@file:OptIn(ExperimentalSerializationApi::class)

package processm.services

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.*
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.serializer
import processm.enhancement.kpi.Report
import processm.helpers.UUIDSerializer
import java.time.LocalDateTime
import java.util.*

val JsonSerializer = Json {
    allowSpecialFloatingPointValues = true
    encodeDefaults = true
    explicitNulls = false
    prettyPrint = false
    serializersModule = SerializersModule {
        include(Report.Json.serializersModule)
        contextual(Any::class, AnySerializer as KSerializer<Any>)
        contextual(LocalDateTime::class, LocalDateTimeSerializer)
        contextual(UUID::class, UUIDSerializer)
    }
}

private object AnySerializer : KSerializer<Any?> {
    override val descriptor: SerialDescriptor = buildClassSerialDescriptor("Any") {}
    private val emptyObjectDescriptor: SerialDescriptor = buildClassSerialDescriptor("EmptyObject") {}

    override fun deserialize(decoder: Decoder): Any? {
        if (!decoder.decodeNotNullMark())
            return null

        decoder as JsonDecoder
        return deserializeJsonElement(decoder.decodeJsonElement())
    }

    private fun deserializeJsonElement(element: JsonElement): Any = when (element) {
        is JsonObject -> element.mapValues { deserializeJsonElement(it.value) }
        is JsonArray -> element.map { deserializeJsonElement(it) }
        is JsonPrimitive -> element.content
        else -> throw IllegalStateException("Unknown object type: $element")
    }

    override fun serialize(encoder: Encoder, value: Any?) {
        if (value === null)
            encoder.encodeNull()
        else if (value::class == Any::class)
            encoder.beginStructure(emptyObjectDescriptor).endStructure(emptyObjectDescriptor)
        else {
            val serializer = encoder.serializersModule.serializer(value.javaClass)
            encoder.encodeSerializableValue(serializer, value)
        }
    }

}

private object LocalDateTimeSerializer : KSerializer<LocalDateTime> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("LocalDateTime", PrimitiveKind.STRING)

    override fun deserialize(decoder: Decoder): LocalDateTime {
        return LocalDateTime.parse(decoder.decodeString())
    }

    override fun serialize(encoder: Encoder, value: LocalDateTime) {
        encoder.encodeString(value.toString())
    }

}
