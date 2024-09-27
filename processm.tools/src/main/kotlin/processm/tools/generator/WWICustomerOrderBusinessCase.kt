package processm.tools.generator

import org.slf4j.LoggerFactory
import processm.tools.helpers.inverseGeometricCDF
import java.sql.Timestamp
import java.time.Instant
import kotlin.math.min
import kotlin.random.Random

/**
 * A worker responsible for simulating a customer and fulfilling its order.
 * The details are presented in `src/main/resources/WWICustomerOrderBusinessCase.pdf`
 *
 * @param [name] Arbitrary string to distinguish this instance of [WWICustomerOrderBusinessCase], for debug purposes only
 * @param [modificationsAllowed] A callable supposed to return `true` if it is allowed for clients to modify their orders
 * before they are completed and `false` otherwise. The purpose is to avoid modifications during the termination phase, which could increase the termination time.
 * @param [pool] A connection pool to the WWI database
 * @param [now] A callable expected to return what the DB is supposed to use as the current time
 * @param [rng] A random number generator
 */
class WWICustomerOrderBusinessCase(
    name: String,
    val modificationsAllowed: () -> Boolean,
    val pool: WWIConnectionPool,
    var now: () -> Instant = Instant::now,
    var rng: Random = Random.Default
) : BusinessCase {

    private val logger = LoggerFactory.getLogger("${this::class.java.name}#$name")
    private val ordersToComplete = ArrayDeque<Int>()
    private var currentOrderID: Int? = null
    private var pickedAnything: Boolean = false
    private var currentInvoiceID: Int? = null
    private var paymentReceived: Boolean = false
    private var delivered: Boolean = false
    private var nFailedPicks: Int = 0
    private var nFailedInvoices: Int = 0

    var successfulDeliveryProbability: Double = .5
        set(value) {
            require(0 < value)
            require(value <= 1)
            field = value
        }

    var paymentBeforeDeliveryProbability: Double = .5
        set(value) {
            require(0 < value)
            require(value <= 1)
            field = value
        }

    var maxDelay: Int = 10

    var orderSize: (Random) -> Int = { rng ->
        val eps = 1e-5 //a small number to avoid zero probability
        inverseGeometricCDF(rng.nextDouble() + eps, 5, 0.9)
    }

    var addLinesProbability: Double = .1
        set(value) {
            require(0 <= value)
            require(value + removeLineProbability < 1)
            field = value
        }

    var removeLineProbability: Double = 0.0
        set(value) {
            require(0 <= value)
            require(value + addLinesProbability < 1)
            field = value
        }

    private fun startOrderProcessing(): BusinessCaseStep {
        check(currentOrderID === null)
        check(ordersToComplete.isNotEmpty())
        currentOrderID = ordersToComplete.removeFirst()
        currentInvoiceID = null
        pickedAnything = false
        paymentReceived = false
        delivered = false
        nFailedPicks = 0
        nFailedInvoices = 0
        logger.debug("starting order processing $currentOrderID")
        return BusinessCaseStep(::tryPick)
    }

    private fun pickOrModify(): BusinessCaseStep {
        val p = rng.nextDouble()
        val canModify = modificationsAllowed()
        if (canModify && addLinesProbability > 0 && p <= addLinesProbability)
            return BusinessCaseStep(::addLines)
        if (canModify && removeLineProbability > 0 && p - addLinesProbability <= removeLineProbability)
            return BusinessCaseStep(::removeLineIfPossible)
        val delay = if (nFailedPicks > 0) rng.nextInt(min(nFailedPicks, maxDelay)).toLong() else 1L
        return BusinessCaseStep(::tryPick, delay)
    }

    private fun addLines(): BusinessCaseStep {
        val orderID = currentOrderID
        checkNotNull(orderID)
        val n = orderSize(rng)
        logger.debug("Adding $n new lines to $orderID")
        pool.addOrderLinesToCustomerOrder(Timestamp.from(now()), Timestamp.from(now()), n, orderID)
        return pickOrModify()
    }

    private fun removeLineIfPossible(): BusinessCaseStep {
        val orderID = currentOrderID
        checkNotNull(orderID)
        logger.debug("Trying to remove a line from $orderID")
        val orderLineID = pool.removeRandomLineFromCustomerOrder(orderID)
        logger.debug("Removed line ID $orderLineID")
        return pickOrModify()
    }

    private fun tryPick(): BusinessCaseStep {
        val orderID = currentOrderID
        checkNotNull(orderID)
        val pickedSomething = pool.pickStockForCustomerOrder(Timestamp.from(now()), orderID)
        logger.debug("Picking for $orderID, picked=$pickedSomething")
        if (pickedSomething) {
            pickedAnything = true
            nFailedPicks = 0
            return pickOrModify()
        }
        if (!pickedAnything) {
            nFailedPicks++
            return pickOrModify()
        }
        return BusinessCaseStep(::backorder)
    }

    private fun backorder(): BusinessCaseStep {
        val orderID = currentOrderID
        checkNotNull(orderID)
        val backorderOrderID: Int? = pool.backorderIfNecessary(Timestamp.from(now()), orderID)
        logger.debug("Backordering for $orderID, backorder orderID=$backorderOrderID")
        if (backorderOrderID != null)
            ordersToComplete.add(backorderOrderID)
        return BusinessCaseStep(::issueInvoice)
    }

    private fun issueInvoice(): BusinessCaseStep {
        check(currentInvoiceID == null)
        val orderID = currentOrderID
        checkNotNull(orderID)
        val invoiceID = pool.invoicePickedOrder(Timestamp.from(now()), orderID)
        logger.debug("Issuing invoice for $orderID, invoiceID=$invoiceID")
        currentInvoiceID = invoiceID
        if (invoiceID == null) {
            nFailedInvoices++
            return BusinessCaseStep(::issueInvoice, rng.nextInt(min(nFailedInvoices, maxDelay)).toLong())
        }
        return BusinessCaseStep(::deliverOrReceivePayment)
    }

    private fun deliverOrReceivePayment(): BusinessCaseStep {
        if (!delivered && !paymentReceived)
            return BusinessCaseStep(if (rng.nextDouble() < paymentBeforeDeliveryProbability) ::receivePayment else ::deliver)
        if (!delivered)
            return BusinessCaseStep(::deliver)
        if (!paymentReceived)
            return BusinessCaseStep(::receivePayment)
        return BusinessCaseStep(::completeOrder)
    }

    private fun deliver(): BusinessCaseStep {
        val invoiceID = currentInvoiceID
        checkNotNull(invoiceID)
        check(!delivered)
        val shouldSucceed = rng.nextDouble() <= successfulDeliveryProbability
        delivered = pool.deliver(Timestamp.from(now()), invoiceID, shouldSucceed)
        logger.debug("Delivering shipment $invoiceID, delivered=$delivered")
        return BusinessCaseStep(::deliverOrReceivePayment)
    }

    private fun receivePayment(): BusinessCaseStep? {
        val invoiceID = currentInvoiceID
        checkNotNull(invoiceID)
        check(!paymentReceived)
        pool.receivePayment(Timestamp.from(now()), invoiceID)
        logger.debug("Payment received for invoice $invoiceID")
        paymentReceived = true
        return BusinessCaseStep(::deliverOrReceivePayment)
    }

    private fun completeOrder(): BusinessCaseStep? {
        check(paymentReceived)
        check(delivered)
        logger.debug("completing order $currentOrderID")
        currentOrderID = null
        return if (ordersToComplete.isNotEmpty())
            BusinessCaseStep(::startOrderProcessing)
        else
            null
    }

    override fun start(): BusinessCaseStep {
        val size = orderSize(rng)
        assert(size >= 1)
        val ts = Timestamp.from(now())
        val orderID = pool.createCustomerOrder(ts, ts, size)
        logger.debug("orderID=$orderID of size=$size")
        ordersToComplete.add(orderID)
        return BusinessCaseStep(::startOrderProcessing)
    }
}