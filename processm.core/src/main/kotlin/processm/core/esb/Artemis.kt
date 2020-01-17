package processm.core.esb

import org.apache.activemq.artemis.api.core.RoutingType
import org.apache.activemq.artemis.core.server.embedded.EmbeddedActiveMQ
import processm.core.logging.enter
import processm.core.logging.exit
import processm.core.logging.logger
import java.net.URL

object Artemis : Service {
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

        logger().exit()
    }

}