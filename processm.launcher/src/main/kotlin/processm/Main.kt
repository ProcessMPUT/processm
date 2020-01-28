package processm

import io.ktor.util.error
import processm.core.esb.Artemis
import processm.core.esb.EnterpriseServiceBus
import processm.core.esb.Hawtio
import processm.core.helpers.loadConfiguration
import processm.core.logging.enter
import processm.core.logging.exit
import processm.core.logging.logger
import processm.core.persistence.Migrator
import processm.services.WebServicesHost
import java.util.*
import kotlin.concurrent.thread

object Main {
    @JvmStatic
    fun main(args: Array<String>) {
        logger().enter()

        try {
            loadConfiguration()

            Migrator.migrate()

            EnterpriseServiceBus().apply {
                // TODO: load the list of services from configuration or discover automatically
                register(Artemis, WebServicesHost, Hawtio)
                startAll()
            }

        } catch (e: Throwable) {
            logger().error(e)
        }

        logger().exit()
    }
}

