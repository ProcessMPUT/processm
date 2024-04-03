package processm.dbmodels.models

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import processm.core.communication.Producer

/**
 * Raises an event about this [WorkspaceComponent] change.
 */
fun WorkspaceComponent.triggerEvent(producer: Producer, event: String = CREATE_OR_UPDATE) {
    producer.produce(WORKSPACE_COMPONENTS_TOPIC) {
        setStringProperty(WORKSPACE_COMPONENT_TYPE, componentType.toString())
        setStringProperty(WORKSPACE_COMPONENT_EVENT, event)
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
fun JsonObject.mostRecentVersion(): Long? = this.keys.mostRecentVersion()

/**
 * Assumes [WorkspaceComponent.data] is a JSON Object with keys suitable for [mostRecentVersion].
 * Returns the primitive associated with the key returned by [mostRecentVersion].
 * If any of the assumptions is not fulfilled, returns null (e.g., not a JSON Object, not a JSON Primitive within the object)
 */
fun WorkspaceComponent.mostRecentData() =
    dataAsJsonObject()?.let { (it[it.mostRecentVersion().toString()] as? JsonPrimitive)?.content }