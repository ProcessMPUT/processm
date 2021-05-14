package processm.core.models.footprint

/**
 * Represents the order relation for a pair of activities (A, B).
 */
enum class Order(val symbol: String) {
    /**
     * A and B are independent.
     */
    NoOrder("#"),

    /**
     * B follows A, and A precedes B.
     */
    FollowedBy("→") {
        override fun invert(): Order = PrecededBy
    },

    /**
     * A follows B, and B precedes A.
     */
    PrecededBy("←") {
        override fun invert(): Order = FollowedBy
    },

    /**
     * A and B are parallel.
     */
    Parallel("∥");

    /**
     * Inverts the order. For the symmetric relations [NoOrder] and [Parallel] it returns the same order, for the
     * antisymmetric relations [FollowedBy] and [PrecededBy] it returns the opposite.
     */
    open fun invert(): Order = this

    override fun toString(): String = symbol
}
