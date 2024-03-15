package processm.dbmodels.models

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
