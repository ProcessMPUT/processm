package processm.core.models.causalnet

import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import processm.core.models.commons.Activity


object DecoupledNodeExecutionSerializer : KSerializer<Activity> {
    override val descriptor: SerialDescriptor = buildClassSerialDescriptor("DecoupledNodeExecution")

    override fun deserialize(decoder: Decoder): Node {
        return decoder.decodeSerializableValue(Node.serializer())
    }

    override fun serialize(encoder: Encoder, value: Activity) {
        value as DecoupledNodeExecution
        encoder.encodeSerializableValue(Node.serializer(), value.activity)
    }
}
