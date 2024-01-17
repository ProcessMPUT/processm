package processm.core.models.processtree

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import processm.core.models.commons.Activity
import processm.core.models.processtree.execution.ActivityExecution
import processm.core.models.processtree.execution.ExecutionNode

@Serializable(with = ProcessTreeActivity.Companion.ProcessTreeActivitySerializer::class)
open class ProcessTreeActivity(name: String) : Node(), Activity {
    companion object {

        @Serializable
        @SerialName("ProcessTreeActivity")
        private class ProcessTreeActivitySurrogate(val name: String)
        object ProcessTreeActivitySerializer : KSerializer<ProcessTreeActivity> {
            override val descriptor: SerialDescriptor = ProcessTreeActivitySurrogate.serializer().descriptor

            override fun deserialize(decoder: Decoder): ProcessTreeActivity =
                ProcessTreeActivity(decoder.decodeSerializableValue(ProcessTreeActivitySurrogate.serializer()).name)

            override fun serialize(encoder: Encoder, value: ProcessTreeActivity) = encoder.encodeSerializableValue(
                ProcessTreeActivitySurrogate.serializer(),
                ProcessTreeActivitySurrogate(value.name)
            )

        }
    }

    /**
     * The name of an activity as a representation of an object
     */
    override val name: String = name.intern()

    /**
     * The symbol of an activity - for activity this will be name
     */
    override val symbol: String
        get() = name

    override fun equals(other: Any?): Boolean {
        if (other === this) return true
        if (other !is ProcessTreeActivity) return false
        return name == other.name && super.equals(other)
    }

    override fun hashCode(): Int {
        var result = super.hashCode()
        result = 31 * result + name.hashCode()
        return result
    }

    override val startActivities: kotlin.sequences.Sequence<ProcessTreeActivity> = sequenceOf(this)

    override val endActivities: kotlin.sequences.Sequence<ProcessTreeActivity> = sequenceOf(this)

    override fun executionNode(parent: ExecutionNode?): ActivityExecution = ActivityExecution(this, parent)

    override fun getLastActivitiesInSubtree() = setOf(this)
}