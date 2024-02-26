package processm.core.models.footprint

import processm.helpers.map2d.DoublingMap2D

/**
 * This is the main method of domain specific language for the initialization of the [Footprint] matrix.
 */
fun footprint(init: FootprintDSL.() -> Unit): Footprint {
    val footprintDSL = FootprintDSL()
    init(footprintDSL)
    return footprintDSL.toFootprint()
}

class FootprintDSL internal constructor() {
    private val matrix = DoublingMap2D<FootprintActivity, FootprintActivity, Order>()

    fun activity(name: String): FootprintActivity = FootprintActivity(name)

    infix fun FootprintActivity.noOrder(other: FootprintActivity) {
        matrix[this, other] = Order.NoOrder
        matrix[other, this] = Order.NoOrder
    }

    infix fun FootprintActivity.`#`(other: FootprintActivity) = noOrder(other)

    infix fun FootprintActivity.followedBy(other: FootprintActivity) {
        matrix[this, other] = Order.FollowedBy
        matrix[other, this] = Order.PrecededBy
    }

    infix fun FootprintActivity.`→`(other: FootprintActivity) = followedBy(other)

    infix fun FootprintActivity.precededBy(other: FootprintActivity) {
        matrix[this, other] = Order.PrecededBy
        matrix[other, this] = Order.FollowedBy
    }

    infix fun FootprintActivity.`←`(other: FootprintActivity) = precededBy(other)

    infix fun FootprintActivity.parallel(other: FootprintActivity) {
        matrix[this, other] = Order.Parallel
        matrix[other, this] = Order.Parallel
    }

    infix fun FootprintActivity.`∥`(other: FootprintActivity) = parallel(other)

    internal fun toFootprint(): Footprint {
        matrix.fillNulls(Order.NoOrder)
        return Footprint(matrix)
    }
}

