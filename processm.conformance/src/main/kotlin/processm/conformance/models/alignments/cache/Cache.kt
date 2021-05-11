package processm.conformance.models.alignments.cache

/**
 * A cache of limited size. Dedicated for the use with aligners to store visited states.
 */
internal class Cache<T> : LinkedHashSet<T>(VISITED_CACHE_INIT, 0.667f) {
    companion object {
        private const val VISITED_CACHE_LIMIT = 1 shl 19
        private const val VISITED_CACHE_INIT = VISITED_CACHE_LIMIT shr 4
    }

    override fun add(element: T): Boolean {
        if (!super.add(element))
            return false

        if (size >= VISITED_CACHE_LIMIT) {
            val it = iterator()
            it.next()
            it.remove()
        }
        assert(size < VISITED_CACHE_LIMIT)

        return true
    }
}
