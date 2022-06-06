package processm.miners

import processm.core.communication.Producer
import processm.dbmodels.models.*

/**
 * Raises an event about this [WorkspaceComponent] change.
 */
fun WorkspaceComponent.triggerEvent(producer: Producer, event: String = CREATE_OR_UPDATE) {
    producer.produce(WORKSPACE_COMPONENTS_TOPIC) {
        setStringProperty(WORKSPACE_COMPONENT_TYPE, componentType.toString())
        setString(WORKSPACE_COMPONENT_EVENT, event)
        setString(WORKSPACE_COMPONENT_ID, id.value.toString())
    }
}
