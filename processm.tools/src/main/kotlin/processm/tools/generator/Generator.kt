package processm.tools.generator

import kotlinx.coroutines.coroutineScope
import org.slf4j.LoggerFactory.getLogger
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

suspend fun main() {
    coroutineScope {
        val company = WWICompany(Configuration(), this)

        Runtime.getRuntime().addShutdownHook(Thread {
            val lock = ReentrantLock()
            val terminated = lock.newCondition()
            val logger = getLogger("shutdownHook")
            logger.info("Shutdown started")
            lock.withLock {
                company.terminate {
                    lock.withLock { terminated.signal() }
                }
                logger.info("Awaiting  termination. It is recommended to wait for clean termination, otherwise the database may be left in an unexpected state.")
                terminated.await()
                logger.info("Terminated")
            }
        })

        company.start()
    }
}