package processm

import io.ktor.util.error
import processm.core.esb.Artemis
import processm.core.logging.enter
import processm.core.logging.exit
import processm.core.logging.logger
import processm.services.WebServicesHost
import java.util.*
import kotlin.concurrent.thread

object Main {
    @JvmStatic
    fun main(args: Array<String>) {
        logger().enter()

        try {
            // Load configuration
            javaClass.classLoader.getResourceAsStream("config.properties").use {
                Properties().apply { load(it) }.forEach { System.setProperty(it.key as String, it.value as String) }
            }

            // List of all services in this application
            val services = listOf(Artemis, WebServicesHost(args))

            // Start the services in individual threads rather than coroutines, as they may block a thread arbitrarily
            // long, and we do not want to block all threads in the thread pool.
            services.forEach {
                thread {
                    try {
                        it.start()

                        // Once the service started, it is safe to register its shutdown hook.
                        Runtime.getRuntime().addShutdownHook(thread(false) {
                            try {
                                it.stop()
                            } catch (e: Throwable) {
                                logger().error("Error occurred when stopping a service", e)
                            }
                        })
                    } catch (e: Throwable) {
                        logger().error("An error occurred when starting a service.", e)
                    }
                }
            }

        } catch (e: Throwable) {
            logger().error(e)
        }

        logger().exit()
    }
}

