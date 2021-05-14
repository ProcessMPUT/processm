package processm.core.models.footprint

import processm.core.helpers.map2d.Map2D
import processm.core.models.commons.Activity

/**
 * Fills nulls in this matrix with the given [order] value.
 */
fun <A : Activity> Map2D<A, A, Order>.fillNulls(order: Order = Order.NoOrder) {
    for (row in rows) {
        for (col in columns) {
            compute(row, col) { _, _, old -> old ?: order }
        }
    }
}
