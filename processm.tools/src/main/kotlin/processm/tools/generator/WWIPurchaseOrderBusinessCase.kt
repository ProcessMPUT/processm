package processm.tools.generator

import processm.tools.helpers.logger
import java.sql.Timestamp
import java.time.Instant
import kotlin.random.Random

/**
 * A worker responsible for finalizing [purchaseOrderID] by waiting for the goods to arrive and then paying the supplier.
 *
 * @param [purchaseOrderID] An ID from `Purchasing.PurchaseOrders` table referring to an incomplete purchase order
 * @param [pool] A connection pool to the WWI database
 * @param [now] A callable expected to return what the DB is supposed to use as the current time
 * @param [rng] A random number generator
 */
class WWIPurchaseOrderBusinessCase(
    val purchaseOrderID: Int,
    val pool: WWIConnectionPool,
    var now: () -> Instant = Instant::now,
    var rng: Random = Random.Default
) : BusinessCase {

    companion object {
        val logger = logger()
    }

    private suspend fun pay(): BusinessCaseStep? {
        pool.paySupplier(Timestamp.from(now()), purchaseOrderID)
        logger.debug("Paying for $purchaseOrderID")
        return null
    }

    override suspend fun start(): BusinessCaseStep =
        if (pool.receivePurchaseOrder(Timestamp.from(now()), purchaseOrderID)) {
            logger.debug("Received gods from $purchaseOrderID")
            BusinessCaseStep(::pay)
        } else {
            logger.debug("Waiting for $purchaseOrderID")
            BusinessCaseStep(::start)
        }
}