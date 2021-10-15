package processm.tools.generator

import processm.tools.helpers.inverseGeometricCDF
import processm.tools.helpers.logger
import java.sql.Timestamp
import java.time.Instant
import kotlin.math.min
import kotlin.random.Random


class WWICustomerOrderBusinessCase(
    val procedures: WWIConnectionPool,
    var now: () -> Instant = Instant::now,
    var rng: Random = Random.Default
) : BussinessCase {

    companion object {
        val logger = logger()
    }

    private val ordersToComplete = ArrayDeque<Int>()
    private var currentOrderID: Int? = null
    private var pickedAnything: Boolean = false
    private var currentInvoiceID: Int? = null
    private var paymentReceived: Boolean = false
    private var delivered: Boolean = false
    private var nFailedPicks: Int = 0
    private var nFailedInvoices: Int = 0

    var successfulDeliveryProbabilty: Double = .5
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

    private suspend fun startOrderProcessing(): BussinessCaseStep {
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
        return BussinessCaseStep(::pick)
    }

    private suspend fun pickOrModify(): BussinessCaseStep {
        val p = rng.nextDouble()
        if (addLinesProbability > 0 && p <= addLinesProbability)
            return BussinessCaseStep(::addLines)
        if (removeLineProbability > 0 && p - addLinesProbability <= removeLineProbability)
            return BussinessCaseStep(::removeLineIfPossible)
        val delay = if (nFailedPicks > 0) rng.nextInt(min(nFailedPicks, maxDelay)).toLong() else 1L
        return BussinessCaseStep(::pick, delay)
    }

    private suspend fun addLines(): BussinessCaseStep {
        val orderID = currentOrderID
        checkNotNull(orderID)
        val n = orderSize(rng)
        logger.debug("Adding $n new lines to $orderID")
        procedures.addOrderLinesToCustomerOrder(Timestamp.from(now()), Timestamp.from(now()), n, orderID)
        return pickOrModify()
    }

    private suspend fun removeLineIfPossible(): BussinessCaseStep {
        val orderID = currentOrderID
        checkNotNull(orderID)
        logger.debug("Trying to remove a line from $orderID")
        val orderLineID = procedures.removeRandomLineFromCustomerOrder(orderID)
        logger.debug("Removed line ID $orderLineID")
        return pickOrModify()
    }

    private suspend fun pick(): BussinessCaseStep {
        val orderID = currentOrderID
        checkNotNull(orderID)
        val pickedSomething = procedures.pickStockForCustomerOrder(Timestamp.from(now()), orderID)
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
        return BussinessCaseStep(::backorder)
    }

    private suspend fun backorder(): BussinessCaseStep {
        val orderID = currentOrderID
        checkNotNull(orderID)
        val backorderOrderID: Int? = procedures.backorderIfNecessary(Timestamp.from(now()), orderID)
        logger.debug("Backordering for $orderID, backorder orderID=$backorderOrderID")
        if (backorderOrderID != null)
            ordersToComplete.add(backorderOrderID)
        return BussinessCaseStep(::issueInvoice)
    }

    private suspend fun issueInvoice(): BussinessCaseStep {
        check(currentInvoiceID == null)
        val orderID = currentOrderID
        checkNotNull(orderID)
        val invoiceID = procedures.invoicePickedOrder(Timestamp.from(now()), orderID)
        logger.debug("Issuing invoice for $orderID, invoiceID=$invoiceID")
        currentInvoiceID = invoiceID
        if (invoiceID == null) {
            nFailedInvoices++
            return BussinessCaseStep(::issueInvoice, rng.nextInt(min(nFailedInvoices, maxDelay)).toLong())
        }
        return BussinessCaseStep(::deliverOrReceivePayment)
    }

    private suspend fun deliverOrReceivePayment(): BussinessCaseStep {
        if (!delivered && !paymentReceived)
            return BussinessCaseStep(if (rng.nextDouble() < paymentBeforeDeliveryProbability) ::receivePayment else ::deliver)
        if (!delivered)
            return BussinessCaseStep(::deliver)
        if (!paymentReceived)
            return BussinessCaseStep(::receivePayment)
        return BussinessCaseStep(::completeOrder)
    }

    private suspend fun deliver(): BussinessCaseStep {
        val invoiceID = currentInvoiceID
        checkNotNull(invoiceID)
        check(!delivered)
        val shouldSucceed = rng.nextDouble() <= successfulDeliveryProbabilty
        delivered = procedures.deliver(Timestamp.from(now()), invoiceID, shouldSucceed)
        logger.debug("Delivering shipment $invoiceID, delivered=$delivered")
        return BussinessCaseStep(::deliverOrReceivePayment)
    }

    private suspend fun receivePayment(): BussinessCaseStep? {
        val invoiceID = currentInvoiceID
        checkNotNull(invoiceID)
        check(!paymentReceived)
        procedures.receivePayment(Timestamp.from(now()), invoiceID)
        logger.debug("Payment received for invoice $invoiceID")
        paymentReceived = true
        return BussinessCaseStep(::deliverOrReceivePayment)
    }

    private suspend fun completeOrder(): BussinessCaseStep? {
        check(paymentReceived)
        check(delivered)
        logger.debug("completing order $currentOrderID")
        currentOrderID = null
        return if (ordersToComplete.isNotEmpty())
            BussinessCaseStep(::startOrderProcessing)
        else
            null
    }

    override suspend fun start(): BussinessCaseStep {
        val size = orderSize(rng)
        assert(size >= 1)
        val ts = Timestamp.from(now())
        val orderID = procedures.createCustomerOrder(ts, ts, size)
        logger.debug("orderID=$orderID of size=$size")
        ordersToComplete.add(orderID)
        return BussinessCaseStep(::startOrderProcessing)
    }
}