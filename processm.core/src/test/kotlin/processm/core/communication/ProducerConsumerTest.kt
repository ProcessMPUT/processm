package processm.core.communication

import jakarta.jms.InvalidDestinationException
import jakarta.jms.Session
import org.junit.jupiter.api.Timeout
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import processm.core.esb.Artemis
import processm.core.esb.getTopicConnectionFactory
import java.util.concurrent.Semaphore
import java.util.concurrent.TimeUnit
import javax.naming.InitialContext
import kotlin.test.AfterTest
import kotlin.test.BeforeTest

class ProducerConsumerTest {


    private val queueName = "consumer"
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
            jmsSession.unsubscribe(queueName)
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

    @ParameterizedTest
    @ValueSource(ints = [500, 501, 2_000])
    @Timeout(10, unit = TimeUnit.SECONDS)
    fun `send and receive`(messageCount: Int) {
        val topic = "TestTopic_send_and_receive_$messageCount"
        val sender = Thread {
            repeat(messageCount) { counter ->
                Producer().produce(topic) {
                    setInt("counter", counter)
                }
            }
        }
        sender.start()
        val received = Semaphore(messageCount)
        val receiver = Thread {
            val consumer = Consumer()
            consumer.listen(topic, queueName) {
                received.release(1)
                true
            }
            consumer.start()
        }
        try {
            receiver.start()
            sender.join()
            received.acquire(messageCount)
        } finally {
            receiver.interrupt()
        }
    }

    @ParameterizedTest
    @ValueSource(ints = [500, 501, 2_000])
    @Timeout(10, unit = TimeUnit.SECONDS)
    fun `only send`(messageCount: Int) {
        val topic = "TestTopic_only_send_$messageCount"
        repeat(messageCount) { counter ->
            Producer().produce(topic) {
                setInt("counter", counter)
            }
        }
    }
}