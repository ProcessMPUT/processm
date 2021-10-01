package processm.tools.generator

import processm.tools.helpers.logger
import java.sql.Timestamp
import java.time.Instant
import kotlin.random.Random


class WWIPuchaseOrderBussinessCase(
    val purchaseOrderID: Int,
    val procedures: WWIConnectionPool,
    var now: () -> Instant = Instant::now,
    var rng: Random = Random.Default
) : BussinessCase {

    companion object {
        val logger = logger()
    }

    private suspend fun pay(): BussinessCaseStep? {
        procedures.paySupplier(Timestamp.from(now()), purchaseOrderID)
        logger.debug("Paying for $purchaseOrderID")
        return null
    }

    override suspend fun start(): BussinessCaseStep =
        if (procedures.receivePurchaseOrder(Timestamp.from(now()), purchaseOrderID)) {
            logger.debug("Received gods from $purchaseOrderID")
            BussinessCaseStep(::pay)
        } else {
            logger.debug("Waiting for $purchaseOrderID")
            //TODO delay
            BussinessCaseStep(::start)
        }
}