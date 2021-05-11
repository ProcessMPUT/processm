package processm.conformance.models.alignments.cache

import processm.core.models.commons.ProcessModelState

@Deprecated("Unused")
internal class AStarHashSetCache : AStarVisitedCache {
    companion object {
        private const val VISITED_CACHE_LIMIT = 100000
        private const val VISITED_CACHE_INIT = VISITED_CACHE_LIMIT / 10
        private const val VISITED_CACHE_FREE = 250
    }

    private val cache = HashSet<CacheEntry>(VISITED_CACHE_INIT)
    override fun isCached(
        lastEventIndex: Int,
        currentCost: Int,
        lastProcessState: ProcessModelState
    ): Boolean = cache.contains(CacheEntry(lastEventIndex, currentCost, lastProcessState))

    override fun addToCache(
        lastEventIndex: Int,
        currentCost: Int,
        lastProcessState: ProcessModelState
    ): Boolean {
        if (!cache.add(CacheEntry(lastEventIndex, currentCost, lastProcessState)))
            return false

        if (cache.size >= VISITED_CACHE_LIMIT) {
            // dropping more states than 1 turns out crucial to run fast
            val it = cache.iterator()
            for (i in 0 until Integer.min(VISITED_CACHE_FREE, cache.size)) {
                it.next()
                it.remove()
            }
        }
        assert(cache.size < VISITED_CACHE_LIMIT)

        return true
    }

    private data class CacheEntry(
        val lastEventIndex: Int,
        val currentCost: Int,
        val lastProcessState: ProcessModelState
    )
}
