package processm.tools.generator

import org.slf4j.LoggerFactory
import processm.tools.helpers.logger
import java.sql.DriverManager
import java.sql.Timestamp
import java.time.Instant
import java.util.*
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.random.Random

/**
 * The core of the generator, separated into two departments:
 *
 * * [customerOrdersDepartment] responsible for generating and then processing orders from customers
 * * [purchaseOrdersDepartment] responsible for placing orders to external suppliers and then following them
 *
 * This separation yields two very distinct groups of business cases, the first one much more complex than the other.
 * The separation is due to the organization of the DB, which keep track of stocks of offered merchandise.
 *
 * @param onTerminate It is called once all the tasks are completed. The termination procedure is initialized by calling [terminate]
 *
 */
class WWICompany(val configuration: Configuration) {

    private val shouldTerminate = AtomicBoolean()
    private val pool = WWIConnectionPool(configuration.connectionPoolSize) {
        DriverManager.getConnection(configuration.dbURL, configuration.dbUser, configuration.dbPassword)
    }
    private val sharedStart: Instant = Instant.now()
    private val customerOrdersDepartmentWorkers = LinkedList<Thread>()

    private lateinit var onTerminate: () -> Unit

    /**
     * Starts termination procedure. Its completion is signalled by calling [onTerminate]
     */
    fun terminate(onTerminate: () -> Unit) {
        this.onTerminate = onTerminate
        shouldTerminate.set(true)
    }

    /**
     * Starts the company. Runs asynchronously [customerOrdersDepartment] and [purchaseOrdersDepartment], and returns immediately.
     */
    fun start() {
        customerOrdersDepartment()
        purchaseOrdersDepartment()
    }

    /**
     * Starts [Configuration.nCustomerOrders] asynchronous instances of [customerOrdersWorker] and returns
     */
    private fun customerOrdersDepartment() =
        repeat(configuration.nCustomerOrders) { i ->
            customerOrdersDepartmentWorkers.add(
                object : Thread() {
                    override fun run() {
                        try {
                            customerOrdersWorker(i)
                        } catch (t: Throwable) {
                            logger().error("Error in customerOrdersWorker", t)
                        }
                    }
                }.apply { start() }
            )
        }

    /**
     * A single worker in the customer orders department, responsible for generating and then completing customer orders by [WWICustomerOrderBusinessCase]
     *
     * Terminates once [shouldTerminate] is set to true and it completes its current order
     */
    private fun customerOrdersWorker(i: Int) =
        with(configuration) {
            val clock = VirtualClock(clockSpeedMultiplier, sharedStart)
            val rng = Random(i * randomSeed)
            val gen =
                WWICustomerOrderBusinessCase(
                    "$i",
                    { !shouldTerminate.get() },
                    pool,
                    clock::invoke,
                    Random(i * customerOrderRandomSeedMultiplier)
                )
            gen.addLinesProbability = customerOrderBusinessCaseAddLinesProbability
            gen.maxDelay = customerOrderBusinessCaseMaxDelay
            gen.paymentBeforeDeliveryProbability = customerOrderBusinessCasePaymentBeforeDeliveryProbability
            gen.removeLineProbability = customerOrderBusinessCaseRemoveLineProbability
            gen.successfulDeliveryProbability = customerOrderBusinessCaseSuccessfulDeliveryProbabilty
            while (!shouldTerminate.get()) {
                gen(rng.nextLong(customerOrderMinStepLength, customerOrderMaxStepLength))
            }
        }

    /**
     * The purchase orders department, periodically places new purchase orders to suppliers in order to satisfy
     * the demand from customers. For each purchase order a new worker following [WWIPurchaseOrderBusinessCase] is spawned.
     *
     * It terminates once all the workers from [customerOrdersDepartment] terminate, to ensure that no customer is left
     * waiting for shipment that never arrives.
     */
    private fun purchaseOrdersDepartment() =
        with(configuration) {
            val threadPool = Executors.newFixedThreadPool(purchaseOrderThreadPoolSize)
            val logger = LoggerFactory.getLogger("purchaseOrders")
            try {
                val clock = VirtualClock(clockSpeedMultiplier, sharedStart)
                var clockSpeedIncreased = false
                val rng = Random(randomSeed)
                while (customerOrdersDepartmentWorkers.isNotEmpty()) {
                    if (shouldTerminate.get()) {
                        logger.debug("I'd die, but ${customerOrdersDepartmentWorkers.size}/${nCustomerOrders} workers is still alive")
                        if (!clockSpeedIncreased) {
                            // Increasing the clock speed so the orders from suppliers are delivered faster. Bit ugly, but the delivery date is handled by the procedures in the DB.
                            clock.multiplier *= purchaseDepartmentAdditionalClockSpeedMultiplierDuringTermination
                            clockSpeedIncreased = true
                        }
                    }
                    Thread.sleep(delayBetweenPlacingPurchaseOrders)
                    for (purchaseOrderID in pool.placePurchaseOrders(Timestamp.from(clock())))
                        threadPool.submit {
                            try {
                                val gen = WWIPurchaseOrderBusinessCase(
                                    purchaseOrderID,
                                    pool,
                                    clock::invoke,
                                    Random(purchaseOrderRandomSeedMultiplier * purchaseOrderID)
                                )
                                gen(rng.nextLong(purchaseOrderMinStepLength, purchaseOrderMaxStepLength))
                            } catch (t: Throwable) {
                                logger.error("Error in WWIPurchaseOrderBusinessCase", t)
                            }
                        }
                    customerOrdersDepartmentWorkers.iterator().also { i ->
                        while (i.hasNext()) {
                            val worker = i.next()
                            if (!worker.isAlive) {
                                worker.join()
                                i.remove()
                                logger.debug("Customer orders department worker terminated")
                            }
                        }
                    }
                }
            } finally {
                logger.debug("I'm die. Thank you forever.")
                onTerminate()
            }
        }
}