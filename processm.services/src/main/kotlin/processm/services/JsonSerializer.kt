@file:OptIn(ExperimentalSerializationApi::class)

package processm.services

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.*
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.overwriteWith
import kotlinx.serialization.serializer
import processm.dbmodels.models.ComponentTypeDto
import processm.helpers.UUIDSerializer
import processm.helpers.serialization.SerializersModuleProvider
import processm.helpers.stats.Distribution
import processm.services.api.models.BPMNComponentData
import processm.services.api.models.CausalNetComponentData
import processm.services.api.models.DirectlyFollowsGraphComponentData
import processm.services.api.models.PetriNetComponentData
import java.time.LocalDateTime
import java.util.*

val JsonSerializer = Json {
    allowSpecialFloatingPointValues = true
    encodeDefaults = true
    explicitNulls = false
    prettyPrint = false
    serializersModule = SerializersModule {
        for (provider in ServiceLoader.load(SerializersModuleProvider::class.java)) {
            include(provider.getSerializersModule())
        }

        contextual(Any::class, AnySerializer as KSerializer<Any>)
        contextual(LocalDateTime::class, LocalDateTimeSerializer)
        contextual(UUID::class, UUIDSerializer)
    }.overwriteWith(SerializersModule {
        contextual(Distribution::class, DistributionWebAPISerializer)
    })
}

private object AnySerializer : KSerializer<Any?> {
    override val descriptor: SerialDescriptor = buildClassSerialDescriptor("Any") {}
    private val emptyObjectDescriptor: SerialDescriptor = buildClassSerialDescriptor("EmptyObject") {}

    override fun deserialize(decoder: Decoder): Any? {
        if (!decoder.decodeNotNullMark())
            return null

        decoder as JsonDecoder
        val jsonElement = decoder.decodeJsonElement()
        val componentType = ((jsonElement as? JsonObject)?.get("type") as? JsonPrimitive)?.content?.let {
            ComponentTypeDto.byTypeNameInDatabase(it)
        }
        return when (componentType) {
            ComponentTypeDto.BPMN ->
                decoder.json.decodeFromJsonElement<BPMNComponentData>(jsonElement)

            ComponentTypeDto.PetriNet ->
                decoder.json.decodeFromJsonElement<PetriNetComponentData>(jsonElement)

            ComponentTypeDto.CausalNet ->
                decoder.json.decodeFromJsonElement<CausalNetComponentData>(jsonElement)

            ComponentTypeDto.DirectlyFollowsGraph ->
                decoder.json.decodeFromJsonElement<DirectlyFollowsGraphComponentData>(jsonElement)

            else -> deserializeJsonElement(jsonElement)
        }
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
        else if (value::class == Any::class || (value is Map<*, *> && value.isEmpty()))
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
        // FIXME: LocalDateTime does not store timezone; better use Instant that is in UTC by definition
        return LocalDateTime.parse(decoder.decodeString().trimEnd('Z'))
    }

    override fun serialize(encoder: Encoder, value: LocalDateTime) {
        encoder.encodeString(value.toString())
    }

}

private object DistributionWebAPISerializer : KSerializer<Distribution> {
    override val descriptor: SerialDescriptor = buildClassSerialDescriptor("DistributionWebAPI") {
        element("min", Double.serializer().descriptor)
        element("Q1", Double.serializer().descriptor)
        element("median", Double.serializer().descriptor)
        element("Q3", Double.serializer().descriptor)
        element("max", Double.serializer().descriptor)
        element("average", Double.serializer().descriptor)
        element("standardDeviation", Double.serializer().descriptor)
    }

    override fun deserialize(decoder: Decoder): Distribution {
        decoder as JsonDecoder
        // FIXME: There was code using decoder.beginStructure etc., very similar to the one used by `serialize`, but
        // FIXME: for some reason it completely failed when Distribution was nested inside a Map.
        with(decoder.decodeJsonElement().jsonObject) {
            val values =
                (0 until descriptor.elementsCount).map { this[descriptor.getElementName(it)]!!.jsonPrimitive.double }
            // FIXME: #254 Distribution actually does not allow for setting individual aggregates and raw data is not available here.
            return Distribution(values.toDoubleArray())
        }
    }

    override fun serialize(encoder: Encoder, value: Distribution) {
        encoder as JsonEncoder
        with(encoder.beginStructure(descriptor)) {
            encodeDoubleElement(descriptor, 0, value.min)
            encodeDoubleElement(descriptor, 1, value.Q1)
            encodeDoubleElement(descriptor, 2, value.median)
            encodeDoubleElement(descriptor, 3, value.Q3)
            encodeDoubleElement(descriptor, 4, value.max)
            encodeDoubleElement(descriptor, 5, value.average)
            encodeDoubleElement(descriptor, 6, value.standardDeviation)
            endStructure(descriptor)
        }
    }

}
