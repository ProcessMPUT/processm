package processm.core.esb

import jakarta.jms.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import processm.logging.logger
import java.util.concurrent.CountDownLatch
import javax.naming.InitialContext
import kotlin.reflect.KClass
import kotlin.test.*


class ArtemisTest {

    private val topic = "TestTopic"
    private val messageCount = 10
    private val artemis = Artemis()


    @BeforeTest
    fun setUp() {
        artemis.register()
        artemis.start()

        // Clean up subscriptions remaining from previous (failing) runs of these tests
        val jmsContext = InitialContext()
        val jmsConnFactory = jmsContext.getTopicConnectionFactory()
        val jmsConnection = jmsConnFactory.createTopicConnection()
        val jmsSession = jmsConnection.createTopicSession(false, Session.AUTO_ACKNOWLEDGE)
        jmsConnection.start()
        try {
            jmsSession.unsubscribe("consumer")
        } catch (e: InvalidDestinationException) {
            // this is expected in case a previous test run did not fail
        }
        jmsConnection.stop()
        jmsSession.close()
        jmsConnection.close()
    }

    @AfterTest
    fun cleanUp() {
        artemis.stop()
    }

    @Test
    fun producerConsumerTest() = runBlocking {

        val consumer = Consumer("consumer", "testTopic", messageCount)
        val producer = Producer("producer", "testTopic", messageCount)

        try {
            // Let the consumer register first to make sure that a durable queue is created before sending a message.
            // Otherwise, it may fail due to race condition.
            consumer.register()
            consumer.start()
            producer.register()
            producer.start()
        } finally {
            // consumer holds count-down mutex, so we stop it first and wait if necessary
            consumer.stop()
            producer.stop()
        }

        assertEquals(messageCount, producer.messages.size)
        assertEquals(messageCount, consumer.messages.size)
        for (i in producer.messages.indices) {
            assertEquals(producer.messages[i], consumer.messages[i])
        }
    }

    @Test
    fun producersConsumersTest() = runBlocking {

        val consumers = arrayOf(
            Consumer("consumer0", "testTopic", messageCount * 2),
            Consumer("consumer1", "testTopic", messageCount * 2)
        )
        val producers = arrayOf(
            Producer("producer0", "testTopic", messageCount),
            Producer("producer1", "testTopic", messageCount)
        )

        try {
            // Let the consumers register first to make sure that a durable queues are created before sending a message.
            // Otherwise, it may fail due to race condition.
            consumers.forEach {
                it.register()
                it.start()
            }
            producers.forEach {
                it.register()
                it.start()
            }
        } finally {
            // consumers hold count-down mutexes, so we stop them first and wait if necessary
            consumers.forEach {
                it.stop()
            }
            producers.forEach {
                it.stop()
            }
        }

        producers.forEach {
            assertEquals(messageCount, it.messageCount)
        }
        consumers.forEach { c ->
            assertEquals(messageCount * consumers.size, c.messageCount)
            producers.forEach { p ->
                assertTrue(c.messages.containsAll(p.messages))
            }
        }
    }
}

class Producer(override val name: String, val topic: String, val messageCount: Int) : Service {
    private lateinit var jmsConnection: TopicConnection
    private lateinit var jmsSession: TopicSession
    private lateinit var jmsTopic: Topic
    private lateinit var jmsPublisher: TopicPublisher

    override var status = ServiceStatus.Unknown
        private set

    override val dependencies: List<KClass<out Service>> = listOf(Artemis::class)

    val messages = mutableListOf<String>()

    override fun register() {
        status = ServiceStatus.Stopped
    }

    override fun start() = runBlocking {

        val jmsContext = InitialContext()
        val jmsConnFactory = jmsContext.getTopicConnectionFactory()
        jmsConnection = jmsConnFactory.createTopicConnection()
        jmsSession = jmsConnection.createTopicSession(false, Session.AUTO_ACKNOWLEDGE)
        jmsTopic = jmsSession.createTopic(topic)
        jmsPublisher = jmsSession.createPublisher(jmsTopic)
        jmsConnection.start()

        repeat(messageCount) {
            val text = "short message $it from producer $name"
            messages.add(text)
            val msg = jmsSession.createTextMessage(text)
            jmsPublisher.publish(msg)
            delay(100)
        }

        status = ServiceStatus.Started
    }

    override fun stop() {
        jmsConnection.stop()
        jmsPublisher.close()
        jmsSession.close()
        jmsConnection.close()

        status = ServiceStatus.Stopped
    }
}

class Consumer(override val name: String, val topic: String, val messageCount: Int) :
    Service, MessageListener, ExceptionListener {
    private lateinit var jmsConnection: TopicConnection
    private lateinit var jmsSession: TopicSession
    private lateinit var jmsTopic: Topic
    private lateinit var jmsSubscriber: MessageConsumer
    private val mutex = CountDownLatch(messageCount)

    val messages = mutableListOf<String>()

    override var status = ServiceStatus.Unknown
        private set

    override val dependencies: List<KClass<out Service>> = listOf(Artemis::class)

    override fun register() {
        status = ServiceStatus.Stopped
    }

    override fun start() {
        val jmsContext = InitialContext()
        val jmsConnFactory = jmsContext.getTopicConnectionFactory()
        jmsConnection = jmsConnFactory.createTopicConnection()
        jmsSession = jmsConnection.createTopicSession(false, Session.AUTO_ACKNOWLEDGE)
        jmsTopic = jmsSession.createTopic(topic)
        jmsSubscriber = jmsSession.createSharedDurableConsumer(jmsTopic, name)
        jmsSubscriber.messageListener = this
        jmsConnection.exceptionListener = this
        jmsConnection.start()

        status = ServiceStatus.Started
    }

    override fun onMessage(msg: Message?) {
        logger().info("Received message $msg")
        messages.add((msg as TextMessage).text)
        mutex.countDown()
    }

    override fun stop() {
        mutex.await()
        jmsSubscriber.close()
        jmsSession.unsubscribe(name)
        jmsSession.close()
        jmsConnection.close()

        status = ServiceStatus.Stopped
    }

    override fun onException(e: JMSException?) {
        logger().error("Consumer.onException", e)
    }
}
