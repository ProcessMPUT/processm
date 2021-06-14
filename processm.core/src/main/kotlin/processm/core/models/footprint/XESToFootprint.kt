package processm.core.models.footprint

import processm.core.helpers.map2d.DoublingMap2D
import processm.core.log.Event
import processm.core.log.XESInputStream
import processm.core.log.hierarchical.Log
import processm.core.log.hierarchical.Trace
import processm.core.log.hierarchical.toFlatSequence

/**
 * Calculates the [Footprint] matrix from this [Log].
 * @throws IllegalArgumentException If any [processm.core.log.Event] has null [processm.core.log.Event.conceptName] property.
 */
fun Log.toFootprint(): Footprint = this.toFlatSequence().toFootprint()

/**
 * Calculates the [Footprint] matrix from this [Trace].
 * @throws IllegalArgumentException If any [processm.core.log.Event] has null [processm.core.log.Event.conceptName] property.
 */
fun Trace.toFootprint(): Footprint = this.toFlatSequence().toFootprint()

fun XESInputStream.toFootprint(): Footprint {
    val matrix = DoublingMap2D<FootprintActivity, FootprintActivity, Order>()
    var prev: FootprintActivity? = null
    for (component in this) {
        when (component) {
            is Log, is Trace -> prev = null
            is Event -> {
                val next =
                    FootprintActivity(requireNotNull(component.conceptName) { "concept:name attribute must be set for all events" })
                if (prev !== null) {
                    if (prev == next) {
                        matrix[prev, next] = Order.Parallel
                    } else {
                        val order = matrix.compute(prev, next) { _, _, old ->
                            when (old) {
                                null -> Order.FollowedBy
                                Order.PrecededBy -> Order.Parallel
                                else -> old // Follows or Parallel
                            }
                        }!!
                        matrix[next, prev] = order.invert()
                    }
                }

                prev = next
            }
        }
    }
    matrix.fillNulls(Order.NoOrder)
    return Footprint(matrix)
}

