package processm.core.communication

import jakarta.jms.MapMessage
import jakarta.jms.Session
import org.messaginghub.pooled.jms.JmsPoolConnectionFactory
import processm.core.esb.getTopicConnectionFactory
import javax.naming.InitialContext

/**
 * JMS message producer. It produces and publishes messages to the specified topic.
 */
class Producer {
    companion object {
        // check https://github.com/messaginghub/pooled-jms/blob/main/pooled-jms-docs/Configuration.md for detailed description
        private const val maxConnections = 1
        private const val connectionIdleTimeout = 30
        private const val connectionCheckInterval: Long = 0L // no check
        private const val useProviderJMSContext = false // no check

        private val jmsPoolingFactory = JmsPoolConnectionFactory()
        private val jmsContext = InitialContext()
        private val jmsConnFactory = jmsContext.getTopicConnectionFactory()

        init {
            jmsPoolingFactory.maxConnections = maxConnections
            jmsPoolingFactory.connectionIdleTimeout = connectionIdleTimeout
            jmsPoolingFactory.connectionCheckInterval = connectionCheckInterval
            jmsPoolingFactory.isUseProviderJMSContext = useProviderJMSContext
            jmsPoolingFactory.connectionFactory = jmsConnFactory
        }
    }

    /**
     * Publishes message to the [topic].
     * @param prepareMessage Message preparation method.
     * @property topic The JMS topic to publish messages to.
     */
    fun produce(topic: String, prepareMessage: MapMessage.() -> Unit) {
        jmsPoolingFactory.createTopicConnection().let { jmsConnection ->
            val jmsSession = jmsConnection.createTopicSession(false, Session.AUTO_ACKNOWLEDGE)
            val jmsTopic = jmsSession.createTopic(topic)
            jmsSession.createPublisher(jmsTopic).use {
                val message = jmsSession.createMapMessage()
                prepareMessage(message)
                it.publish(message)
            }
        }
    }
}
