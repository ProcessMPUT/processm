package processm.dbmodels.models

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*
import processm.core.communication.Producer

private val WCEproducer = Producer()

/**
 * Raises an event about this [WorkspaceComponent] change.
 * @param producer A JMS producer to use.
 * @param event The domain-specific type of the triggered event.
 * @param eventData The data of the event. May be null.
 */
fun WorkspaceComponent.triggerEvent(
    producer: Producer = WCEproducer,
    event: String = CREATE_OR_UPDATE,
    eventData: String? = null
) {
    producer.produce(WORKSPACE_COMPONENTS_TOPIC) {
        setStringProperty(WORKSPACE_COMPONENT_TYPE, componentType.toString())
        setStringProperty(WORKSPACE_COMPONENT_EVENT, event)
        eventData?.let { setStringProperty(WORKSPACE_COMPONENT_EVENT_DATA, it) }
        setString(WORKSPACE_COMPONENT_ID, id.value.toString())
        if (event == DATA_CHANGE) {
            setString(WORKSPACE_ID, workspace.id.toString())
        }
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
fun Map<String, JsonElement>.mostRecentVersion(): Long? = this.keys.mostRecentVersion()

/**
 * Assumes [WorkspaceComponent.data] is a JSON Object with keys suitable for [mostRecentVersion].
 * Returns the [JsonElement] associated with the key returned by [mostRecentVersion].
 * If any of the assumptions is not fulfilled, returns null (e.g., not a JSON Object)
 */
fun WorkspaceComponent.mostRecentData(): JsonElement? =
    dataAsJsonObject()?.let { it[it.mostRecentVersion().toString()] }

@Serializable
data class ComponentData(
    val modelId: String,
    val alignmentKPIId: String
) {
    fun toJsonElement(): JsonElement = Json.encodeToJsonElement(this)
}

/**
 * Converts [JsonElement] to [ComponentData].
 */
fun JsonElement.asComponentData(): ComponentData = Json.decodeFromJsonElement<ComponentData>(this)


