package processm.miners.processtree.directlyfollowsgraph

class Arc {
    var cardinality: Int = 0
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
}