package processm.conformance.models.alignments.cache

import com.github.benmanes.caffeine.cache.Caffeine
import processm.core.models.commons.ProcessModelState

@Deprecated("Unused")
class AStarCaffeineCache : AStarVisitedCache {
    companion object {
        private const val VISITED_CACHE_LIMIT = 100000L
        private const val VISITED_CACHE_INIT = VISITED_CACHE_LIMIT.toInt() / 10
    }

    private val cache = Caffeine.newBuilder()
        .initialCapacity(VISITED_CACHE_INIT)
        .maximumSize(VISITED_CACHE_LIMIT)
        .build<CacheEntry, Unit>()

    override fun isCached(lastEventIndex: Int, currentCost: Int, lastProcessState: ProcessModelState): Boolean =
        cache.getIfPresent(CacheEntry(lastEventIndex, currentCost, lastProcessState)) !== null

    override fun addToCache(lastEventIndex: Int, currentCost: Int, lastProcessState: ProcessModelState): Boolean {
        val entry = CacheEntry(lastEventIndex, currentCost, lastProcessState)
        if (cache.getIfPresent(entry) !== null)
            return false
        cache.put(entry, Unit)
        return true
    }

    private data class CacheEntry(
        val lastEventIndex: Int,
        val currentCost: Int,
        val lastProcessState: ProcessModelState
    )
}
