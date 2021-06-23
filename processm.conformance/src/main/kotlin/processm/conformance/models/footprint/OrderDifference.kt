package processm.conformance.models.footprint

import processm.core.models.footprint.Order

/**
 * Represents the difference between [logOrder] and [modelOrder].
 */
data class OrderDifference(
    val logOrder: Order,
    val modelOrder: Order
) {
    override fun toString(): String = "${logOrder.symbol}:${modelOrder.symbol}"
}
