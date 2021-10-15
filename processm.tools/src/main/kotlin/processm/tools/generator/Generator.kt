package processm.tools.generator

import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import org.slf4j.LoggerFactory.getLogger
import java.sql.DriverManager
import java.sql.Timestamp
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.locks.ReentrantLock
import kotlin.random.Random


suspend fun main() {
    val shouldTerminate = AtomicBoolean()
    val lock = ReentrantLock()
    val terminated = lock.newCondition()
    Runtime.getRuntime().addShutdownHook(Thread {
        val logger = getLogger("shutdownHook")
        logger.info("Shutdown started")
        try {
            lock.lock()
            shouldTerminate.set(true)
            logger.info("Awaiting  termination")
            terminated.await()
            logger.info("Terminated")
        } finally {
            lock.unlock()
        }
    })

    with(Configuration) {
        val pool = WWIConnectionPool(connectionPoolSize) {
            DriverManager.getConnection(dbURL, dbUser, dbPassword)
        }

        coroutineScope {
            val rng = Random(randomSeed)
            val clock = VirtualClock(clockSpeedMultiplier)
            repeat(nCustomerOrders) { i ->
                async {
                    val gen =
                        WWICustomerOrderBusinessCase(
                            pool,
                            clock::invoke,
                            Random(i * customerOrderRandomSeedMultiplier)
                        )
                    gen.addLinesProbability = customerOrderBusinessCaseAddLinesProbability
                    gen.maxDelay = customerOrderBusinessCaseMaxDelay
                    gen.paymentBeforeDeliveryProbability = customerOrderBusinessCasePaymentBeforeDeliveryProbability
                    gen.removeLineProbability = customerOrderBusinessCaseRemoveLineProbability
                    gen.successfulDeliveryProbabilty = customerOrderBusinessCaseSuccessfulDeliveryProbabilty
                    while (!shouldTerminate.get())
                        gen(rng.nextLong(customerOrderMinStepLength, customerOrderMaxStepLength))
                }
            }
            async {
                while (!shouldTerminate.get()) {
                    delay(delayBetweenPlacingPurchaseOrders)
                    for (purchaseOrderID in pool.placePurchaseOrders(Timestamp.from(clock()))) async {
                        val gen = WWIPuchaseOrderBusinessCase(
                            purchaseOrderID,
                            pool,
                            clock::invoke,
                            Random(purchaseOrderRandomSeedMultiplier * purchaseOrderID)
                        )
                        gen(rng.nextLong(purchaseOrderMinStepLength, purchaseOrderMaxStepLength))
                    }
                }
            }.invokeOnCompletion {
                try {
                    lock.lock()
                    terminated.signal()
                } finally {
                    lock.unlock()
                }
            }
        }
    }
}