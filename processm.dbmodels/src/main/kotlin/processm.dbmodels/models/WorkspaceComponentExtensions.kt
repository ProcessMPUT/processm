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


/**
 * A process model component can store multiple models computed for different snapshots of data. Moreover, for each
 * model there can be multiple secondary artifacts, such as alignments, that depend on the model, and on the data.
 * Hence, the class uses two distinct, yet related notions: model version and data version.
 * They both originate in the version number returned by [processm.core.log.hierarchical.DBHierarchicalXESInputStream.readVersion],
 * thus are both assumed to be monotonic.
 * Model version is the version number of the data the model was computed on.
 * Data version is the version number of the data the secondary artifact (e.g., the alignments) was computed on.
 * For example, alignments with data version 4 and model version 2 were computed by aligning a log returned by the stream
 * with `readVersion()=4` to a model computed on a log with `readVersion()=2`
 */
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

    /**
     * The component for displaying this data
     */
    @Transient
    lateinit var component: WorkspaceComponent
        private set

    /**
     * @see alignmentKPIReports
     */
    @SerialName("alignment_kpi_report")
    private val mutableAlignmentKPIReports =
        HashMap<Long, HashMap<Long, @Serializable(with = URISerializer::class) URI>>()

    /**
     * @see models
     */
    @SerialName("models")
    private val mutableModels = HashMap<Long, String>()

    /**
     * Alignment KPI reports relevant for the component. The outer key is the model version, the inner key is the data version
     * (typically, the data key >= the model key), and the value is an URI corresponding to the actual KPI report.
     */
    @Transient
    val alignmentKPIReports: Map<Long, Map<Long, URI>>
        get() = mutableAlignmentKPIReports

    /**
     * Maps model versions to their DB IDs
     */
    @Transient
    val models: Map<Long, String>
        get() = mutableModels

    /**
     * The model version accepted by the user. It is the default version displayed to the user, and the version the new
     * alignments are computed against.
     */
    @SerialName("accepted_model_version")
    var acceptedModelVersion: Long? = null
        set(value) {
            requireNotNull(value)
            require(value in models)
            field = value
        }

    /**
     * Equivalent to `models[acceptedModelVersion]`
     */
    @Transient
    val acceptedModelId: String?
        get() = models[acceptedModelVersion]

    /**
     * Add a new alignment KPI report identified by [reportId], computed on the model `model[modelVersion]` by aligning
     * the data with the version number [dataVersion]
     */
    fun addAlignmentKPIReport(modelVersion: Long, dataVersion: Long, reportId: URI) {
        mutableAlignmentKPIReports.computeIfAbsent(modelVersion) { HashMap() }[dataVersion] = reportId
    }

    /**
     * Returns the URI of the alignment KPI report computed by aligning the data with the version number [dataVersion]
     * to the model computed on the data with the version number [modelVersion] (i.e., on the model `models[modelVersion]`),
     * or `null` if such a report is not available.
     */
    fun getAlignmentKPIReport(modelVersion: Long, dataVersion: Long): URI? {
        return mutableAlignmentKPIReports[modelVersion]?.get(dataVersion)
    }

    /**
     * Returns the URI of the most-recent alignment KPI report (i.e., computed on the data with the highest version number)
     * for the model identified by the version [modelVersion]. If [modelVersion] is `null`, [acceptedModelVersion] is used.
     */
    fun getMostRecentAlignmentKPIReport(modelVersion: Long? = null): URI? =
        (modelVersion ?: acceptedModelVersion)?.let { modelVersion ->
            mutableAlignmentKPIReports[modelVersion]?.maxByOrNull { it.key }?.value
        }

    /**
     * Returns the data version of the most-recent alignment KPI report (i.e., computed on the data with the highest version number)
     * for the model identified by the version [modelVersion]. If [modelVersion] is `null`, [acceptedModelVersion] is used.
     */
    fun getMostRecentAlignmentKPIReportVersion(modelVersion: Long? = null): Long? =
        (modelVersion ?: acceptedModelVersion)?.let { modelVersion ->
            mutableAlignmentKPIReports[modelVersion]?.maxOf { it.key }
        }

    /**
     * Serializes this data structure to JSON, for DB storage or network transmission
     */
    fun toJSON(): String = Json.encodeToString(this)

    /**
     * Returns `true` if [version] is a valid key for [models]
     */
    fun hasModel(version: Long) = models.containsKey(version)

    /**
     * Adds or replaces the model identified by [modelId] using the version number [version]
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