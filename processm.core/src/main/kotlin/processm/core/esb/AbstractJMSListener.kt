package processm.core.esb

import jakarta.jms.*
import processm.core.logging.loggedScope
import javax.naming.InitialContext

/**
 * The base class for JMS Listener. Facilitates the implementation of the JMS message consumers in services.
 * @property topicName The JMS topic to listen to.
 * @property filter The topic filter. Possibly null.
 * @property name The unique listener name.
 */
abstract class AbstractJMSListener(
    protected val topicName: String,
    protected val filter: String?,
    protected val name: String,
    protected val durable: Boolean = true
) : JMSListener {
    companion object {
        protected val jmsContext = InitialContext()
        protected val jmsConnFactory = jmsContext.getTopicConnectionFactory()
    }

    protected var connection: TopicConnection? = null
    protected var session: TopicSession? = null
    protected var consumer: MessageConsumer? = null

    override fun listen() = loggedScope { logger ->
        // configure
        connection = jmsConnFactory.createTopicConnection() // this line requires Artemis working
        connection!!.exceptionListener = this

        session = connection!!.createTopicSession(false, Session.AUTO_ACKNOWLEDGE)
        val topic = session!!.createTopic(topicName)


        consumer =
            if (durable) session!!.createSharedDurableConsumer(topic, name, filter)
            else session!!.createSubscriber(topic, filter, false)
        consumer!!.messageListener = this

        // run
        connection!!.start()

        // report
        logger.debug("Listening to topic ${topic.topicName} using consumer $name.")
    }

    override fun onException(exception: JMSException?) = loggedScope { logger ->
        logger.error(
            "Error receiving message in topic $topicName with filter $filter using consumer $name.",
            exception
        )
    }

    override fun close(): Unit = loggedScope {
        consumer?.close()
        session?.close()
        connection?.close()
    }
}
