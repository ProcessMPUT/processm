package processm.core

import jakarta.jms.MapMessage
import jakarta.jms.Message
import processm.core.esb.AbstractJMSListener
import java.util.*
import java.util.concurrent.Semaphore

/**
 * Observes in a non-durable fashion the JMS [topic] for messages matching the [filter]. The [waitForMessage] method
 * waits for a matching message. Each matching message releases a mutex.
 *
 * This class is intended for the use in tests for waiting for messages created by async services. The use of
 * [waitForMessage] is preferred than e.g. [Thread.sleep] call, as it prevents wasting time.
 */
class TopicObserver(
    val topic: String,
    val filter: String
) : AutoCloseable {

    private val mutex = Semaphore(1)
    private val notificationService = object : AbstractJMSListener(
        topic,
        filter,
        "$topic observer ${UUID.randomUUID()}",
        false
    ) {
        override fun onMessage(message: Message?) {
            message as MapMessage
            mutex.release()
        }
    }

    /**
     * Connects to JMS service and starts observing the topic.
     */
    fun start() {
        reset()
        notificationService.listen()
    }

    /**
     * Sets the state to blocked and waiting for messages.
     */
    fun reset() {
        mutex.drainPermits()
    }

    /**
     * Blocks current thread until a message arrives in [topic] that matches the [filter].
     */
    fun waitForMessage() {
        mutex.acquire()
    }

    override fun close() {
        notificationService.close()
    }
}
