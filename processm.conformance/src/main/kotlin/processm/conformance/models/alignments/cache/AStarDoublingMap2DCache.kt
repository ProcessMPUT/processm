package processm.conformance.models.alignments.cache

import processm.core.helpers.map2d.DoublingMap2D
import processm.core.models.commons.ProcessModelState

@Deprecated("Unused")
internal class AStarDoublingMap2DCache : AStarVisitedCache {
    companion object {
        private const val VISITED_CACHE_LIMIT = 50000
        private const val VISITED_CACHE_INIT = VISITED_CACHE_LIMIT / 10
        private const val VISITED_CACHE_FREE = 250
    }

    private val cache = DoublingMap2D<Int, Int, HashSet<ProcessModelState>>()
    override fun isCached(
        lastEventIndex: Int,
        currentCost: Int,
        lastProcessState: ProcessModelState
    ): Boolean =
        cache[lastEventIndex, currentCost]?.contains(lastProcessState) == true

    override fun addToCache(
        lastEventIndex: Int,
        currentCost: Int,
        lastProcessState: ProcessModelState
    ): Boolean {
        val v = cache.compute(lastEventIndex, currentCost) { _, _, old ->
            old ?: HashSet(VISITED_CACHE_INIT)
        }!!

        if (!v.add(lastProcessState))
            return false

        if (v.size >= VISITED_CACHE_LIMIT) {
            // dropping more states than 1 turns out crucial to run fast
            val it = v.iterator()
            for (i in 0 until Integer.min(VISITED_CACHE_FREE, v.size)) {
                it.next()
                it.remove()
            }
        }
        assert(v.size < VISITED_CACHE_LIMIT)

        return true
    }
}
