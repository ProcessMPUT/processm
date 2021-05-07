package processm.conformance.models.alignments.cache

import processm.core.models.commons.ProcessModelState

@Deprecated("Unused")
internal interface AStarVisitedCache {
    fun isCached(
        lastEventIndex: Int,
        currentCost: Int,
        lastProcessState: ProcessModelState
    ): Boolean

    fun addToCache(
        lastEventIndex: Int,
        currentCost: Int,
        lastProcessState: ProcessModelState
    ): Boolean
}
