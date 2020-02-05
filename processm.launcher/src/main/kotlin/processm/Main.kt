package processm

import processm.core.esb.EnterpriseServiceBus
import processm.core.helpers.loadConfiguration
import processm.core.logging.enter
import processm.core.logging.exit
import processm.core.logging.logger
import processm.core.persistence.Migrator

object Main {
    @JvmStatic
    fun main(args: Array<String>) {
        logger().enter()

        try {
            loadConfiguration()

            Migrator.migrate()

            EnterpriseServiceBus().apply {
                autoRegister()
                startAll()
            }

        } catch (e: Throwable) {
            logger().error("A fatal error occurred during initialization.", e)
        }

        logger().exit()
    }
}

