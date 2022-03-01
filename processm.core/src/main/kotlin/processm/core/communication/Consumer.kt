package processm.core.communication

import processm.core.esb.getTopicConnectionFactory
import processm.core.logging.loggedScope
import java.io.Closeable
import javax.jms.*
import javax.naming.InitialContext

class Consumer : Closeable {
    companion object {
        private val jmsContext = InitialContext()
        private val jmsConnFactory = jmsContext.getTopicConnectionFactory()
    }

    private val connection = lazy { jmsConnFactory.createTopicConnection() } // this line requires Artemis working
    private val session = lazy { connection.value.createTopicSession(false, Session.CLIENT_ACKNOWLEDGE) }
    private val topicConsumers = mutableMapOf<String, MessageConsumer>()
    private val messageHandlers = mutableMapOf<String, (Map<String, String>, Map<String, String>) -> Boolean>()

    fun listen(topic: String, queueName: String, messageHandler: (Map<String, String>, Map<String, String>) -> Boolean) {
        topicConsumers.computeIfAbsent(topic) { it ->
            val jmsTopic = session.value.createTopic(it)
            val consumer = session.value.createSharedDurableConsumer(jmsTopic, queueName)

            consumer.messageListener = MessageListener { message ->
                loggedScope { logger ->
                    require(message is MapMessage) { "Unrecognized message $message." }
                    val headersMap = message.propertyNames
                        .toList()
                        .map { "$it" }
                        .associateWith { propertyName -> message.getString(propertyName) }
                    val messageMap = message.mapNames
                        .toList()
                        .map { "$it" }
                        .associateWith { mapName -> message.getString(mapName) }
                    try {
                        if (messageHandlers[topic]?.invoke(messageMap, headersMap) == true) message.acknowledge()
                    } catch (e: Exception) {
                        logger.warn("An error occurred while invoking handler for $topic topic", e)
                    }
                }
            }

            return@computeIfAbsent consumer
        }

        messageHandlers[topic] = messageHandler
        connection.value.start()
    }

    override fun close() {
        messageHandlers.clear()
        topicConsumers.forEach { (_, consumer) ->
            consumer.close()
        }
        topicConsumers.clear()
        if (session.isInitialized()) session.value.close()
        if (connection.isInitialized()) connection.value.close()
    }
}