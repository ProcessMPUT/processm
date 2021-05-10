package processm.core.models.footprint

import processm.core.helpers.map2d.Map2D

/**
 * Fills nulls in this matrix with the given [order] value.
 */
fun Map2D<FootprintActivity, FootprintActivity, Order>.fillNulls(order: Order = Order.NoOrder) {
    for (row in rows) {
        for (col in columns) {
            compute(row, col) { _, _, old -> old ?: order }
        }
    }
}
