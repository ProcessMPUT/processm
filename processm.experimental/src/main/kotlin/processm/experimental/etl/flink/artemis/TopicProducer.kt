package processm.experimental.etl.flink.artemis

import jakarta.jms.Session
import jakarta.jms.TopicConnection
import jakarta.jms.TopicPublisher
import jakarta.jms.TopicSession
import processm.core.esb.Artemis
import processm.core.esb.Service
import processm.core.esb.ServiceStatus
import processm.core.logging.enter
import processm.core.logging.exit
import processm.core.logging.logger
import javax.naming.InitialContext
import kotlin.reflect.KClass

open class TopicProducer<T : java.io.Serializable>(val topic: String) : Service {

    @Transient
    private val logger = logger()

    override var status: ServiceStatus = ServiceStatus.Unknown
        protected set
    override val name: String = "$topic: Producer"
    override val dependencies: List<KClass<out Service>> = listOf(Artemis::class)


    private lateinit var jmsConnection: TopicConnection
    private lateinit var jmsSession: TopicSession
    private lateinit var jmsPublisher: TopicPublisher

    override fun register() {
        logger.enter()
        status = ServiceStatus.Stopped
        logger.exit()
    }

    override fun start() {
        try {
            logger.enter()
            val jmsContext = InitialContext()
            val jmsConnFactory = JMSUtils.getDefaultTopicConnectionFactory(jmsContext)
            jmsConnection = jmsConnFactory.createTopicConnection()
            jmsSession = jmsConnection.createTopicSession(false, Session.AUTO_ACKNOWLEDGE)
            jmsPublisher = jmsSession.createPublisher(jmsSession.createTopic(topic))
            jmsConnection.start()
            status = ServiceStatus.Started
        } finally {
            logger.exit()
        }
    }

    override fun stop() {
        try {
            logger.enter()
            send(null)
            jmsPublisher.close()
            jmsSession.close()
            jmsConnection.close()
            status = ServiceStatus.Stopped
        } finally {
            logger.exit()
        }
    }

    fun send(e: T?) {
        try {
            logger.enter()
            val msg = if (e != null)
                jmsSession.createObjectMessage(e)
            else
                jmsSession.createTextMessage("EOS")
            jmsPublisher.publish(msg)
        } finally {
            logger.exit()
        }
    }

}
