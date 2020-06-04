package processm.miners.processtree.directlyfollowsgraph

class Arc {
    var cardinality: Int = 0
        private set

    internal fun increment(): Arc {
        cardinality++
        return this
    }

    internal fun decrement(): Arc {
        if (cardinality > 0)
            cardinality--
        
        return this
    }
}