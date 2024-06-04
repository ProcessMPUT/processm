package processm.dbmodels.models

import kotlinx.serialization.*
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.Json
import processm.core.communication.Producer
import java.net.URI

private val WCEproducer = Producer()

/**
 * Raises an event about this [WorkspaceComponent] change.
 * @param producer A JMS producer to use.
 * @param event The domain-specific type of the triggered event.
 * @param eventData The data of the event. May be null.
 */
fun WorkspaceComponent.triggerEvent(
    producer: Producer = WCEproducer,
    event: WorkspaceComponentEventType,
    eventData: String? = null
) {
    producer.produce(WORKSPACE_COMPONENTS_TOPIC) {
        setStringProperty(WORKSPACE_COMPONENT_TYPE, componentType.toString())
        setStringProperty(WORKSPACE_COMPONENT_EVENT, event.toString())
        eventData?.let { setStringProperty(WORKSPACE_COMPONENT_EVENT_DATA, it) }
        setString(WORKSPACE_COMPONENT_ID, id.value.toString())
        if (event == WorkspaceComponentEventType.DataChange) {
            setString(WORKSPACE_ID, workspace.id.toString())
        }
    }
}

fun WorkspaceComponent.triggerEvent(producer: Producer = WCEproducer, eventData: DataChangeType) =
    triggerEvent(producer = producer, event = WorkspaceComponentEventType.DataChange, eventData = eventData.toString())


@Serializable
class ProcessModelComponentData private constructor() {
    companion object {
        fun create(component: WorkspaceComponent) =
            (component.data?.let { Json.decodeFromString<ProcessModelComponentData>(it) }
                ?: ProcessModelComponentData()).apply {
                this.component = component
            }

        private object URISerializer : KSerializer<URI> {
            override val descriptor: SerialDescriptor
                get() = PrimitiveSerialDescriptor("URI", PrimitiveKind.STRING)

            override fun deserialize(decoder: Decoder): URI = URI(decoder.decodeString())

            override fun serialize(encoder: Encoder, value: URI) = encoder.encodeString(value.toString())
        }
    }

    @Transient
    lateinit var component: WorkspaceComponent

    @SerialName("alignment_kpi_report")
    private val mutableAlignmentKPIReports =
        HashMap<Long, HashMap<Long, @Serializable(with = URISerializer::class) URI>>()

    @SerialName("models")
    private val mutableModels = HashMap<Long, String>()

    @Transient
    val alignmentKPIReports: Map<Long, Map<Long, URI>>
        get() = mutableAlignmentKPIReports

    @Transient
    val models: Map<Long, String>
        get() = mutableModels

    @SerialName("accepted_model_version")
    var acceptedModelVersion: Long? = null
        set(value) {
            requireNotNull(value)
            require(value in models)
            field = value
        }

    @Transient
    val acceptedModelId: String?
        get() = models[acceptedModelVersion]

    fun addAlignmentKPIReport(modelVersion: Long, dataVersion: Long, reportId: URI) {
        mutableAlignmentKPIReports.computeIfAbsent(modelVersion) { HashMap() }[dataVersion] = reportId
    }

    fun getAlignmentKPIReport(modelVersion: Long, dataVersion: Long): URI? {
        return mutableAlignmentKPIReports[modelVersion]?.get(dataVersion)
    }

    fun getMostRecentAlignmentKPIReport(modelVersion: Long? = null): URI? =
        (modelVersion ?: acceptedModelVersion)?.let { modelVersion ->
            mutableAlignmentKPIReports[modelVersion]?.maxByOrNull { it.key }?.value
        }

    fun toJSON(): String = Json.encodeToString(this)

    fun hasModel(version: Long) = models.containsKey(version)

    /**
     * @return `true` if the model became the accepted model, `false` otherwise
     */
    fun addModel(version: Long, modelId: String): Boolean {
        (models as MutableMap)[version] = modelId
        if (acceptedModelVersion == null) {
            acceptedModelVersion = version
            return true
        }
        return false
    }
}