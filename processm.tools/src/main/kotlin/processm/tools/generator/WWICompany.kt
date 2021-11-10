package processm.tools.generator

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import org.slf4j.LoggerFactory
import java.sql.DriverManager
import java.sql.Timestamp
import java.time.Instant
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
class WWICompany(val configuration: Configuration, val coroutineScope: CoroutineScope) {

    private val shouldTerminate = AtomicBoolean()
    private val pool = WWIConnectionPool(configuration.connectionPoolSize) {
        DriverManager.getConnection(configuration.dbURL, configuration.dbUser, configuration.dbPassword)
    }
    private val terminatedCustomerOrders = Channel<Int>()
    private val sharedStart: Instant = Instant.now()

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
    fun start(): Unit = with(coroutineScope) {
        async { customerOrdersDepartment() }
        async { purchaseOrdersDepartment() }
    }

    /**
     * Starts [Configuration.nCustomerOrders] asynchronous instances of [customerOrdersWorker] and returns
     */
    private suspend fun customerOrdersDepartment() =
        with(coroutineScope) {
            repeat(configuration.nCustomerOrders) { i ->
                async { customerOrdersWorker(i) }
            }
        }

    /**
     * A single worker in the customer orders department, responsible for generating and then completing customer orders by [WWICustomerOrderBusinessCase]
     *
     * Terminates once [shouldTerminate] is set to true and it completes its current order
     */
    private suspend fun customerOrdersWorker(i: Int) =
        with(configuration) {
            try {
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
                while (!shouldTerminate.get())
                    gen(rng.nextLong(customerOrderMinStepLength, customerOrderMaxStepLength))
            } finally {
                terminatedCustomerOrders.send(i)
            }
        }

    /**
     * The purchase orders department, periodically places new purchase orders to suppliers in order to satisfy
     * the demand from customers. For each purchase order a new worker following [WWIPurchaseOrderBusinessCase] is spawned.
     *
     * It terminates once all the workers from [customerOrdersDepartment] terminate, to ensure that no customer is left
     * waiting for shipment that never arrives.
     */
    private suspend fun purchaseOrdersDepartment() = with(coroutineScope) {
        with(configuration) {
            val logger = LoggerFactory.getLogger("purchaseOrders")
            try {
                val clock = VirtualClock(clockSpeedMultiplier, sharedStart)
                var clockSpeedIncreased = false
                val rng = Random(randomSeed)
                var nTerminatedClients = 0
                while (nTerminatedClients < nCustomerOrders) {
                    if (shouldTerminate.get()) {
                        logger.debug("I'd die, but only $nTerminatedClients/${nCustomerOrders} clients terminated")
                        if (!clockSpeedIncreased) {
                            // Increasing the clock speed so the orders from suppliers are delivered faster. Bit ugly, but the delivery date is handled by the procedures in the DB.
                            clock.multiplier *= purchaseDepartmentAdditionalClockSpeedMultiplierDuringTermination
                            clockSpeedIncreased = true
                        }
                    }
                    delay(delayBetweenPlacingPurchaseOrders)
                    for (purchaseOrderID in pool.placePurchaseOrders(Timestamp.from(clock())))
                        async {
                            val gen = WWIPurchaseOrderBusinessCase(
                                purchaseOrderID,
                                pool,
                                clock::invoke,
                                Random(purchaseOrderRandomSeedMultiplier * purchaseOrderID)
                            )
                            gen(rng.nextLong(purchaseOrderMinStepLength, purchaseOrderMaxStepLength))
                        }
                    while (true) {
                        val result = terminatedCustomerOrders.tryReceive()
                        if (result.isSuccess) {
                            // ignore the actual content of the message, it is unimportant
                            nTerminatedClients++
                        } else {
                            if (result.isClosed) {
                                logger.warn("`terminatedCustomerOrders` is closed. This is unexpected.")
                                nTerminatedClients = nCustomerOrders
                            }
                            break
                        }
                    }
                }
            } finally {
                logger.debug("I'm die. Thank you forever.")
                onTerminate()
            }
        }
    }
}