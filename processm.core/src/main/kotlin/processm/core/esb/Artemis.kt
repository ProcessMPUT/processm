package processm.core.esb

import jakarta.jms.TopicConnectionFactory
import org.apache.activemq.artemis.core.server.embedded.EmbeddedActiveMQ
import processm.logging.enter
import processm.logging.exit
import processm.logging.logger
import java.util.concurrent.Semaphore
import javax.naming.Context

/**
 * The name of the connection factory for retrieving the [TopicConnectionFactory] for the embedded Artemis instance.
 */
private const val TOPIC_CONNECTION_FACTORY_NAME = "ConnectionFactory"

/**
 * Retrieves the [TopicConnectionFactory] for the embedded Artemis instance.
 */
fun Context.getTopicConnectionFactory(): TopicConnectionFactory =
    this.lookup(TOPIC_CONNECTION_FACTORY_NAME) as TopicConnectionFactory

class Artemis : Service {
    companion object {
        /**
         * Mutex for preventing running of multiple instance of Artemis concurrently.
         * It is the case in concurrent tests, where some tests create own instances of Artemis.
         * Since all instances share the same configuration (and data directories), it may lead to random test fails.
         */
        private val runMutex = Semaphore(1)
    }

    private lateinit var broker: EmbeddedActiveMQ

    override val name = "Artemis"

    override var status = ServiceStatus.Unknown
        private set

    override fun register() {
        logger().enter()

        broker = EmbeddedActiveMQ()
        status = ServiceStatus.Stopped

        logger().exit()
    }

    override fun start() {
        logger().enter()

        runMutex.acquire()

        logger().info("Staring Apache ActiveMQ Artemis broker")
        broker.start()
        status = ServiceStatus.Started
        val acceptors = broker.activeMQServer.configuration.acceptorConfigurations.map { it.name }.joinToString(", ")
        logger().info("Apache ActiveMQ Artemis broker started; acceptors: $acceptors")

        logger().exit()
    }

    override fun stop() {
        logger().enter()

        logger().info("Stopping Apache ActiveMQ Artemis broker")
        broker.stop()
        status = ServiceStatus.Stopped
        logger().info("Apache ActiveMQ Artemis broker stopped")

        runMutex.release()

        logger().exit()
    }

}
