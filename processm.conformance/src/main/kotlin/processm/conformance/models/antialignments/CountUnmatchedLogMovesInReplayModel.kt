package processm.conformance.models.antialignments

import processm.conformance.models.alignments.CountUnmatchedLogMoves
import processm.core.log.Event
import processm.core.models.commons.Activity
import processm.core.models.commons.ProcessModelState
import processm.helpers.Counter

private fun HashMap<String, Int>.countEvent(e: Event) {
    e.conceptName?.let { merge(it, 1, Int::plus) }
}

internal class CountUnmatchedLogMovesInReplayModel(val model: ReplayModel) : CountUnmatchedLogMoves {

    private val modelCounts = ArrayList<Counter<String>>()
    private val cache = ArrayList<HashMap<String, Int>>()
    private var lastTrace: List<Event>? = null

    override fun reset() {
        modelCounts.forEach { it.clear() }
        while (modelCounts.size < model.trace.size)
            modelCounts.add(Counter())
        for (i in model.trace.indices) {
            val a = model.trace[i]
            if (!a.isSilent)
                for (j in 0..i)
                    modelCounts[j].inc(a.name)
        }
        lastTrace = null
    }

    override fun compute(
        startIndex: Int,
        trace: List<Event>,
        prevProcessState: ProcessModelState,
        curActivity: Activity?
    ): Int {
        prevProcessState as ReplayModelState
        if (startIndex >= trace.size)
            return 0

        // identity check - we assume the list is immutable
        if (trace !== lastTrace) {
            lastTrace = trace
            cache.forEach { it.clear() }
            while (cache.size < trace.size) {
                cache.add(HashMap())
            }
            cache[trace.size - 1].countEvent(trace[trace.size - 1])
            for (i in trace.size - 2 downTo 0) {
                with(cache[i]) {
                    putAll(cache[i + 1])
                    countEvent(trace[i])
                }
            }
        }
        val i = prevProcessState.index + (if (curActivity !== null) 1 else 0)
        if (i < modelCounts.size) {
            var total = 0
            val other = modelCounts[i]
            for ((k, v) in cache[startIndex]) {
                val d = v - other[k]
                if (d > 0)
                    total += d
            }
            return total
        } else
            return cache[startIndex].values.sum()
    }

}