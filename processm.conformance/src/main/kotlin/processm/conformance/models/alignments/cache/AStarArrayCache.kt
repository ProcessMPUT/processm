package processm.conformance.models.alignments.cache

import processm.core.models.commons.ProcessModelState

@Deprecated("Unused")
internal class AStarArrayCache : AStarVisitedCache {
    companion object {
        private const val VISITED_CACHE_LIMIT = 50000
        private const val VISITED_CACHE_INIT = VISITED_CACHE_LIMIT / 10
        private const val VISITED_CACHE_FREE = 250
    }

    private val cache = ArrayList<ArrayList<HashSet<ProcessModelState>>>()
    override fun isCached(
        lastEventIndex: Int,
        currentCost: Int,
        lastProcessState: ProcessModelState
    ): Boolean {
        if (lastEventIndex < 0 || lastEventIndex >= cache.size)
            return false
        val costToState = cache[lastEventIndex]
        return currentCost < costToState.size && costToState[currentCost].contains(lastProcessState)
    }

    override fun addToCache(
        lastEventIndex: Int,
        currentCost: Int,
        lastProcessState: ProcessModelState
    ): Boolean {
        if (lastEventIndex < 0)
            return true
        while (cache.size <= lastEventIndex)
            cache.add(ArrayList())

        val costToState = cache[lastEventIndex]
        while (costToState.size <= currentCost)
            costToState.add(HashSet(VISITED_CACHE_INIT))

        val v = costToState[currentCost]
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
