package processm.miners.processtree.directlyfollowsgraph

class Arc {
    var cardinality: Int = 0
        private set

    internal fun increment(): Arc {
        cardinality++
        return this
    }
}