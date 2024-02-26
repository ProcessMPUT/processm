package processm.experimental.etl.flink.artemis

import jakarta.jms.*
import processm.core.esb.Artemis
import processm.core.esb.Service
import processm.core.esb.ServiceStatus
import processm.logging.enter
import processm.logging.exit
import processm.logging.logger
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.Semaphore
import java.util.concurrent.atomic.AtomicBoolean
import javax.naming.InitialContext
import kotlin.reflect.KClass

open class TopicConsumer<T : java.io.Serializable>(val topic: String) : Service, Iterator<T>, MessageListener {


    private lateinit var jmsConnection: TopicConnection
    private lateinit var jmsSession: TopicSession
    private lateinit var jmsConsumer: MessageConsumer
    private val streamClosed = AtomicBoolean()

    override fun register() {
        val logger = logger()
        try {
            logger.enter()
            status = ServiceStatus.Stopped
        } finally {
            logger.exit()
        }
    }

    override var status: ServiceStatus = ServiceStatus.Unknown
    override val name: String = "$topic: Consumer"
    override val dependencies: List<KClass<out Service>> = listOf(Artemis::class)

    override fun start() {
        val logger = logger()
        try {
            logger.enter()
            check(!streamClosed.get())
            val jmsContext = InitialContext()

            val jmsConnFactory = JMSUtils.getDefaultTopicConnectionFactory(jmsContext)
            jmsConnection = jmsConnFactory.createTopicConnection()
            jmsSession = jmsConnection.createTopicSession(false, Session.AUTO_ACKNOWLEDGE)
            jmsConsumer = jmsSession.createSharedDurableConsumer(jmsSession.createTopic(topic), name)

//        jmsConsumer = jmsSession.createConsumer(jmsSession.createTopic(topic))
            jmsConsumer.messageListener = this
            jmsConnection.start()

            messages = LinkedBlockingQueue<T>()
            sem = Semaphore(0)

            status = ServiceStatus.Started
        } finally {
            logger.exit()
        }
    }

    override fun stop() {
        val logger = logger()
        try {
            logger.enter()
            jmsConsumer.close()
            jmsSession.unsubscribe(name)
            jmsSession.close()
            jmsConnection.close()
            status = ServiceStatus.Stopped
            streamClosed.set(true)
            sem.release()
        } finally {
            logger.exit()
        }
    }

    protected lateinit var messages: LinkedBlockingQueue<T>
    protected var lastMessage: T? = null
    protected lateinit var sem: Semaphore

    override fun onMessage(message: Message?) {
        val logger = logger()
        try {
            logger.enter()
            if (message != null) {
                //TODO TopicProducer chyba jednak nie powinien byc w stanie zamknac consumera - przy Queue moze ma to sens, ale przy topicach nie
                if (message is ObjectMessage)
                    messages.add(message.`object` as T)
                sem.release()
            }
        } finally {
            logger.exit()
        }
    }

    /**
     * If a message is available -> returns true
     * If the stream is closed -> returns false
     * Otherwhise waits for either of the above
     */
    override fun hasNext(): Boolean {
        val logger = logger()
        try {
            logger.enter()
            if (lastMessage != null)
                return true
            if (streamClosed.get())
                return false
            sem.acquire()
            if (messages.isEmpty()) {
                stop()
                return false
            } else {
                lastMessage = messages.take()
                return true
            }
        } finally {
            logger.exit()
        }
    }

    /**
     * Never hangs - either returns available message or throws something
     */
    override fun next(): T {
        val logger = logger()
        try {
            logger.enter()
            val result = lastMessage!!
            lastMessage = null
            return result
        } finally {
            logger.exit()
        }
    }

}
