package processm.miners

import processm.core.esb.getTopicConnectionFactory
import processm.dbmodels.models.*
import javax.jms.Session
import javax.jms.TopicConnection
import javax.jms.TopicPublisher
import javax.jms.TopicSession
import javax.naming.InitialContext


private val jmsContext = InitialContext()
private val jmsConnFactory = jmsContext.getTopicConnectionFactory()

/**
 * Raises an event about this [WorkspaceComponent] change.
 */
fun WorkspaceComponent.triggerEvent(event: String = CREATE_OR_UPDATE) {
    var jmsConnection: TopicConnection? = null
    var jmsSession: TopicSession? = null
    var jmsPublisher: TopicPublisher? = null
    try {
        jmsConnection = jmsConnFactory.createTopicConnection()
        jmsSession = jmsConnection.createTopicSession(false, Session.AUTO_ACKNOWLEDGE)
        val jmsTopic = jmsSession.createTopic(WORKSPACE_COMPONENTS_TOPIC)
        jmsPublisher = jmsSession.createPublisher(jmsTopic)
        val message = jmsSession.createMapMessage()

        message.setStringProperty(WORKSPACE_COMPONENT_TYPE, componentType.toString())
        message.setString(WORKSPACE_COMPONENT_EVENT, event)
        message.setString(WORKSPACE_COMPONENT_ID, id.value.toString())

        jmsPublisher.publish(message)
    } finally {
        jmsPublisher?.close()
        jmsSession?.close()
        jmsConnection?.close()
    }
}
