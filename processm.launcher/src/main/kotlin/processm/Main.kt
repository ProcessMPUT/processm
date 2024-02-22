package processm

import processm.core.esb.EnterpriseServiceBus
import processm.core.loadConfiguration
import processm.logging.loggedScope
import kotlin.concurrent.thread

object Main {
    /**
     * The main entry point for ProcessM.
     */
    @JvmStatic
    fun main(args: Array<String>) = loggedScope { logger ->
        try {
            loadConfiguration()

            val esb = EnterpriseServiceBus().apply {
                autoRegister()
                startAll()
            }

            Runtime.getRuntime().addShutdownHook(thread(false) {
                esb.close()
            })

        } catch (e: Throwable) {
            logger.error("A fatal error occurred during initialization.", e)
        }
    }
}

