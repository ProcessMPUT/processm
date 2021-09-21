package processm

import processm.core.esb.EnterpriseServiceBus
import processm.core.helpers.loadConfiguration
import processm.core.logging.enter
import processm.core.logging.exit
import processm.core.logging.logger
import java.util.concurrent.ForkJoinPool
import java.util.concurrent.TimeUnit

object Main {
    /**
     * The timeout for awaiting concurrent operations in the common pool when closing.
     */
    private const val CONCURRENT_OPERATIONS_TIMEOUT = 10000L

    @JvmStatic
    fun main(args: Array<String>) {
        logger().enter()

        try {
            loadConfiguration()

            EnterpriseServiceBus().apply {
                autoRegister()
                startAll()
            }

        } catch (e: Throwable) {
            logger().error("A fatal error occurred during initialization.", e)
        } finally {
            ForkJoinPool.commonPool().awaitQuiescence(CONCURRENT_OPERATIONS_TIMEOUT, TimeUnit.MILLISECONDS)
        }

        logger().exit()
    }
}

