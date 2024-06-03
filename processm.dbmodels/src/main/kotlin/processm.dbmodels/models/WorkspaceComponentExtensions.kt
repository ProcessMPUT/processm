package processm.dbmodels.models

import jakarta.jms.MapMessage
import kotlinx.serialization.json.*
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
    eventData: String? = null,
    putData: MapMessage.() -> Unit = {}
) {
    producer.produce(WORKSPACE_COMPONENTS_TOPIC) {
        setStringProperty(WORKSPACE_COMPONENT_TYPE, componentType.toString())
        setStringProperty(WORKSPACE_COMPONENT_EVENT, event.toString())
        eventData?.let { setStringProperty(WORKSPACE_COMPONENT_EVENT_DATA, it) }
        setString(WORKSPACE_COMPONENT_ID, id.value.toString())
        if (event == WorkspaceComponentEventType.DataChange) {
            setString(WORKSPACE_ID, workspace.id.toString())
        }
        putData(this)
    }
}

private val JsonElement?.safeJsonPrimitive
    get() = this as? JsonPrimitive?

private val JsonElement?.safeJsonObject
    get() = this as? JsonObject?

const val ACCEPTED_MODEL_VERSION: String = "accepted_model_version"
const val MODELS = "models"
const val ALIGNMENT_KPI_REPORTS = "alignment_kpi_report"

//TODO these two should live elsewhere
const val MODEL_VERSION = "model_version"
const val DATA_VERSION = "data_version"

class ProcessModelComponentData(val component: WorkspaceComponent) {
    companion object {
        private const val HAS_CONCEPT_DRIFT = "has_concept_drift"
    }

    private val data = component.dataAsJsonObject().orEmpty()

    //TODO this doesnt seem efficient
    private val mutableAlignmentKPIReports =
        data[ALIGNMENT_KPI_REPORTS]?.safeJsonObject?.mapValuesTo(HashMap()) {
            it.value.safeJsonObject?.toMutableMap() ?: mutableMapOf()
        } ?: mutableMapOf()
    private val mutableModels = data[MODELS]?.safeJsonObject?.toMutableMap() ?: mutableMapOf()

    val alignmentKPIReports: Map<Long, Map<Long, URI>>
        get() {
            val result = HashMap<Long, HashMap<Long, URI>>()
            for ((modelVersion, entries) in mutableAlignmentKPIReports) {
                val partial = HashMap<Long, URI>()
                for ((dataVersion, uri) in entries)
                    partial[dataVersion.toLong()] = URI(uri.jsonPrimitive.content)
                result[modelVersion.toLong()] = partial
            }
            return result
        }

    val models: Map<String, String>
        get() = mutableModels.mapValues { it.value.jsonPrimitive.content }

    var acceptedModelVersion: Long? = data[ACCEPTED_MODEL_VERSION]?.safeJsonPrimitive?.longOrNull
        set(value) {
            requireNotNull(value)
            require(value.toString() in mutableModels)
            if (field != value) {
                field = value
                hasConceptDrift = false
            }
        }

    var hasConceptDrift: Boolean = data[HAS_CONCEPT_DRIFT]?.safeJsonPrimitive?.booleanOrNull ?: false

    val acceptedModelId: String? =
        data[ACCEPTED_MODEL_VERSION]?.safeJsonPrimitive?.content?.let { acceptedModelVersion ->
            mutableModels[acceptedModelVersion]?.safeJsonPrimitive?.content
        }

    /**
     * For a fixed value of [modelVersion], it assumes reports are added in the ascending order of [dataVersion].
     * Breaking this assumption may break [hasConceptDrift]
     */
    fun addAlignmentKPIReport(modelVersion: Long, dataVersion: Long, reportId: URI) {
        mutableAlignmentKPIReports.computeIfAbsent(modelVersion.toString()) { mutableMapOf() }[dataVersion.toString()] =
            JsonPrimitive(reportId.toString())
        if (modelVersion == acceptedModelVersion)
            hasConceptDrift = false
    }

    fun getAlignmentKPIReport(modelVersion: Long, dataVersion: Long): URI? {
        return mutableAlignmentKPIReports[modelVersion.toString()]?.get(dataVersion.toString())?.safeJsonPrimitive?.content
            ?.let { URI(it) }
    }

    fun getMostRecentAlignmentKPIReport(modelVersion: Long? = null): URI? =
        (modelVersion ?: acceptedModelVersion)?.let { modelVersion ->
            mutableAlignmentKPIReports[modelVersion.toString()]?.mostRecentEntry()?.safeJsonPrimitive?.let {
                URI(it.content)
            }
        }

    fun toJSON(): String =
        JsonObject(data.toMutableMap().apply {
            acceptedModelVersion?.let { put(ACCEPTED_MODEL_VERSION, JsonPrimitive(it)) }
            put(ALIGNMENT_KPI_REPORTS, JsonObject(mutableAlignmentKPIReports.mapValues { JsonObject(it.value) }))
            put(MODELS, JsonObject(mutableModels))
            put(HAS_CONCEPT_DRIFT, JsonPrimitive(hasConceptDrift))
        }).toString()

    fun hasModel(version: Long) = mutableModels.containsKey(version.toString())

    /**
     * @return `true` if the model became the accepted model, `false` otherwise
     */
    fun addModel(version: Long, modelId: String): Boolean {
        mutableModels[version.toString()] = JsonPrimitive(modelId)
        if (acceptedModelVersion == null) {
            acceptedModelVersion = version
            return true
        }
        return false
    }
}

/**
 * Tries to parse [WorkspaceComponent.data] as a [JsonObject]. Returns the parsed object if successful, and `null` otherwise.
 */
fun WorkspaceComponent.dataAsJsonObject() =
    data?.let { runCatching { Json.parseToJsonElement(it) } }?.getOrNull() as? JsonObject

internal fun Iterable<String>.mostRecentVersion(): Long? = this.fold<String, Long?>(null) { prev, item ->
    val current = item.toLongOrNull() ?: return@fold prev
    return@fold if (prev !== null && prev > current) prev else current
}

/**
 * Assumes keys represent version numbers, thus tries to parse every key as [Long]. Returns the highest value of all the
 * keys that were successfully parsed, or  `null` otherwise (i.e., if the object is empty or none of the keys were
 * successfully parsed).
 */
fun <T> Map<String, T>.mostRecentVersion(): Long? = this.keys.mostRecentVersion()

fun <T> Map<String, T>.mostRecentEntry(): T? = mostRecentVersion()?.let { get(it.toString()) }


