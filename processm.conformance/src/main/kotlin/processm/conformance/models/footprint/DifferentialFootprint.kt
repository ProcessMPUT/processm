package processm.conformance.models.footprint

import processm.conformance.models.ConformanceModel
import processm.core.models.footprint.Footprint
import processm.core.models.footprint.FootprintActivity
import processm.core.models.footprint.Order
import processm.helpers.map2d.DoublingMap2D

/**
 * A conformance model based on [Footprint]s.
 * See Wil var der Aalst, Process Mining: Data Science in Action, Chapter 8.4.
 *
 * @property logFootprint The [Footprint] of log.
 * @property modelFootprint The [Footprint] of model.
 */
data class DifferentialFootprint(
    val logFootprint: Footprint,
    val modelFootprint: Footprint
) : ConformanceModel {

    /**
     * The fitness of [logFootprint] to [modelFootprint], i.e., how much of the behavior in the log is reproducible
     * using the model. The fitness attains value from 0 (no behavior is reproducible) to 1 (all behavior is reproducible).
     */
    val fitness: Double = 1.0 - getCost(logFootprint, modelFootprint).toDouble() / logFootprint.matrix.size

    /**
     * The precision of [modelFootprint] w.r.t. [logFootprint], i.e., how much of the behavior of the model is observed
     * in the log. The precision attains value from 0 (no behavior is observed) to 1 (all behavior is observed).
     */
    val precision: Double = 1.0 - getCost(modelFootprint, logFootprint).toDouble() / modelFootprint.matrix.size

    private fun getCost(base: Footprint, other: Footprint): Int =
        base.activities.sumOf { a1 ->
            base.activities.sumOf { a2 ->
                if (isSuborder(base.matrix[a1, a2] ?: Order.NoOrder, other.matrix[a1, a2] ?: Order.NoOrder)) 0
                else 1.toInt()
            }
        }

    private fun isSuborder(suborder: Order, order: Order): Boolean =
        suborder == order
                || suborder == Order.NoOrder
                || order == Order.Parallel

    init {
        assert(fitness in 0.0..1.0) { fitness }
        assert(precision in 0.0..1.0) { precision }
    }

    val difference: FootprintDifference by lazy(LazyThreadSafetyMode.PUBLICATION) {
        val activities = modelFootprint.activities.toHashSet()
        activities.addAll(logFootprint.activities)
        val matrix = DoublingMap2D<FootprintActivity, FootprintActivity, OrderDifference>()

        for (a1 in activities) {
            for (a2 in activities) {
                val logOrder = logFootprint.matrix[a1, a2] ?: Order.NoOrder
                val modelOrder = modelFootprint.matrix[a1, a2] ?: Order.NoOrder
                if (logOrder != modelOrder)
                    matrix[a1, a2] = OrderDifference(logOrder, modelOrder)
            }
        }

        FootprintDifference(matrix)
    }

    override fun toString(): String = buildString {
        append("log:\n")
        append(logFootprint.toString())
        append("model:\n")
        append(modelFootprint.toString())
        append("difference:\n")
        append(difference.toString())
    }
}
