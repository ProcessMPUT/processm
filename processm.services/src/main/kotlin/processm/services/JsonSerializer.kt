package processm.services

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.Json
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.serializer
import processm.enhancement.kpi.Report
import java.time.LocalDateTime

val JsonSerializer = Json {
    allowSpecialFloatingPointValues = true
    encodeDefaults = true
    explicitNulls = false
    prettyPrint = false
    serializersModule = SerializersModule {
        include(Report.Json.serializersModule)
        contextual(Any::class, AnySerializer as KSerializer<Any>)
        contextual(LocalDateTime::class, LocalDateTimeSerializer)
    }
}

private object AnySerializer : KSerializer<Any?> {
    override val descriptor: SerialDescriptor = buildClassSerialDescriptor("Any") {}

    override fun deserialize(decoder: Decoder): Any? {
        throw UnsupportedOperationException("Deserialization of Any field is not supported")
    }

    @OptIn(ExperimentalSerializationApi::class)
    override fun serialize(encoder: Encoder, value: Any?) {
        if (value === null)
            encoder.encodeNull()
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
