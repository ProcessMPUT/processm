package processm.dbmodels.models

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
