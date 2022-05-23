package processm.core.communication

import processm.core.esb.getTopicConnectionFactory
import processm.core.logging.loggedScope
import java.io.Closeable
import java.util.*
import javax.jms.*
import javax.naming.InitialContext
import kotlin.concurrent.schedule

/**
 * JMS message consumer. It listens to messages published to the specified topics.
 */
class Consumer : Closeable {
    companion object {
        private const val connectionStateCheckingInterval = 20_000L
        private val jmsContext = InitialContext()
        private val jmsConnFactory = jmsContext.getTopicConnectionFactory()
        private val instances = mutableSetOf<Consumer>()
        private val connectionsWatcher = Timer(true)

        init {
            connectionsWatcher.schedule(connectionStateCheckingInterval, connectionStateCheckingInterval) {
                instances
                    .filter { it.wasStarted }
                    .forEach { consumer ->
                        loggedScope { logger ->
                            try {
                                consumer.start()
                            } catch (e: Exception) {
                                logger.warn("Failed to restore consumer instance", e)
                            }
                        }
                    }
            }
        }
    }

    private val topicConsumers = mutableMapOf<Pair<String, String>, MessageConsumer>()
    private val messageHandlers = mutableMapOf<Pair<String, String>, (MapMessage) -> Boolean>()

    /**
     * Indicates whether the current instance has received a call to [Consumer.start] at any moment in time.
     */
    private var wasStarted = false
    private var connection: TopicConnection? = null
    private var session: TopicSession? = null
    private val connectionLock = Object()

    private fun setUpConnection() {
        synchronized(connectionLock) {
            connection = jmsConnFactory.createTopicConnection()
            connection!!.exceptionListener = ExceptionListener { e ->
                loggedScope { logger ->
                    logger.warn("The connection to Artemis was broken", e)
                    closeConnection()
                }
            }
            session = connection!!.createTopicSession(false, Session.CLIENT_ACKNOWLEDGE)
            messageHandlers.forEach { (topicToQueue, messageHandler) -> addConsumer(topicToQueue.first, topicToQueue.second, messageHandler) }
            connection!!.start()
        }
    }

    private fun addConsumer(topicName: String, queueName: String, messageHandler: (MapMessage) -> Boolean) {
        synchronized(connectionLock) {
            messageHandlers[topicName to queueName] = messageHandler

            if (session == null) return
            val jmsTopic = session!!.createTopic(topicName)
            val consumer = session!!.createSharedDurableConsumer(jmsTopic, queueName)

            consumer.messageListener = MessageListener { message ->
                loggedScope { logger ->
                    try {
                        if (messageHandler(message as MapMessage)) message.acknowledge()
                    } catch (e: Exception) {
                        logger.warn("An error occurred while invoking handler for $topicName topic", e)
                    }
                }
            }

            topicConsumers[topicName to queueName] = consumer
        }
    }

    private fun closeConnection() {
        synchronized(connectionLock) {
            topicConsumers.forEach { (_, consumer) -> consumer.close() }
            topicConsumers.clear()
            session?.close()
            connection?.close()
            connection = null
        }
    }

    init {
        instances.add(this)
    }

    /**
     * Starts receiving messages to be processed by handler provided with [listen] method.
     * The method can be called multiple times.
     */
    fun start() {
        wasStarted = true
        if (connection == null) setUpConnection()
    }

    /**
     * Listens to the specified [topicName] and executes defined method against received messages.
     * @param topicName The topic to listen to.
     * @param queueName The name of the queue storing the messages.
     * @param messageHandler The method executed on received messages.
     */
    fun listen(topicName: String, queueName: String, messageHandler: (MapMessage) -> Boolean) {
        addConsumer(topicName, queueName, messageHandler)
    }

    override fun close() {
        instances.remove(this)
        messageHandlers.clear()
        closeConnection()
    }
}