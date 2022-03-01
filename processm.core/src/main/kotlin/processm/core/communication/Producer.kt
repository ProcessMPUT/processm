package processm.core.communication

import processm.core.esb.getTopicConnectionFactory
import java.io.Closeable
import javax.jms.Session
import javax.naming.InitialContext

class Producer : Closeable {
    companion object {
        private val jmsContext = InitialContext()
        private val jmsConnFactory = jmsContext.getTopicConnectionFactory()
    }

    private val jmsConnection = jmsConnFactory.createTopicConnection()
    private val jmsSession = jmsConnection.createTopicSession(false, Session.AUTO_ACKNOWLEDGE)

    fun produce(topic: String, messageContent: Map<String, String>, messageHeaders: Map<String, String> = emptyMap()) {
        val jmsTopic = jmsSession.createTopic(topic)
        jmsSession.createPublisher(jmsTopic).use {
            val message = jmsSession.createMapMessage()

            messageHeaders.forEach { (key, value) -> message.setStringProperty(key, value) }
            messageContent.forEach { (key, value) -> message.setString(key, value) }
            it.publish(message)
        }
    }

    override fun close() {
        jmsSession.close()
        jmsConnection.close()
    }
}