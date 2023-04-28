package processm.miners.processtree.directlyfollowsgraph

class Arc(cardinality: Int = 0) {
    var cardinality: Int = cardinality
        private set

    internal fun increment(): Arc {
        cardinality++
        return this
    }

    internal fun decrement(): Arc {
        check(cardinality > 0) { "Cardinality of connection between activities less than should be." }

        cardinality--
        return this
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Arc) return false

        if (cardinality != other.cardinality) return false

        return true
    }

    override fun hashCode(): Int {
        return cardinality
    }
}
