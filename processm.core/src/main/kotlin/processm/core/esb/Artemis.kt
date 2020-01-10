package processm.core.esb

import org.apache.activemq.artemis.api.core.RoutingType
import org.apache.activemq.artemis.core.server.embedded.EmbeddedActiveMQ
import processm.core.logging.enter
import processm.core.logging.exit
import processm.core.logging.logger
import java.net.URL

object Artemis : ServiceBus {
    private val broker = EmbeddedActiveMQ();
    override val busUrl: URL
        get() = TODO("broker.activeMQServer.configuration")

    override fun start() {
        logger().enter()

        logger().info("Staring Apache ActiveMQ Artemis broker")
        broker.start()
        logger().info("Apache ActiveMQ Artemis broker started; acceptors: ${broker.activeMQServer.configuration.acceptorConfigurations.map { it.name }.joinToString(", ")}")

        logger().exit()
    }

    override fun stop() {
        logger().enter()

        logger().info("Stopping Apache ActiveMQ Artemis broker")
        broker.stop()
        logger().info("Apache ActiveMQ Artemis broker stopped")

        logger().exit()
    }

}