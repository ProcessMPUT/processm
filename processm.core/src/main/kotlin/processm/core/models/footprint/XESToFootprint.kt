package processm.core.models.footprint

import processm.core.helpers.map2d.DoublingMap2D
import processm.core.log.hierarchical.Log
import processm.core.log.hierarchical.Trace

/**
 * Calculates the [Footprint] matrix from this [Log].
 * @throws IllegalArgumentException If any [processm.core.log.Event] has null [processm.core.log.Event.conceptName] property.
 */
fun Log.toFootprint(): Footprint {
    val matrix = DoublingMap2D<FootprintActivity, FootprintActivity, Order>()
    for (trace in this.traces)
        trace.toFootprintInternal(matrix)
    matrix.fillNulls(Order.NoOrder)
    return Footprint(matrix)
}

/**
 * Calculates the [Footprint] matrix from this [Trace].
 * @throws IllegalArgumentException If any [processm.core.log.Event] has null [processm.core.log.Event.conceptName] property.
 */
fun Trace.toFootprint(): Footprint {
    val matrix = DoublingMap2D<FootprintActivity, FootprintActivity, Order>()
    this.toFootprintInternal(matrix)
    matrix.fillNulls(Order.NoOrder)
    return Footprint(matrix)
}

private fun Trace.toFootprintInternal(matrix: DoublingMap2D<FootprintActivity, FootprintActivity, Order>) {
    val iterator = this.events.iterator()
    if (!iterator.hasNext())
        return

    var prev = FootprintActivity(
        requireNotNull(iterator.next().conceptName) { "concept:name attribute must be set for all events" }
    )

    while (iterator.hasNext()) {
        val next = FootprintActivity(
            requireNotNull(iterator.next().conceptName) { "concept:name attribute must be set for all events" }
        )

        val order = matrix.compute(prev, next) { _, _, old ->
            when (old) {
                null -> Order.FollowedBy
                Order.PrecededBy -> Order.Parallel
                else -> old // Follows or Parallel
            }
        }!!
        matrix[next, prev] = order.invert()

        prev = next
    }
}
