package processm.conformance.models.antialignments

import processm.conformance.models.alignments.CountUnmatchedLogMoves
import processm.core.log.Event
import processm.core.models.commons.Activity
import processm.core.models.commons.ProcessModelState
import processm.helpers.Counter

private fun Counter<String>.countActivity(a: Activity?) {
    if (a !== null && !a.isSilent)
        dec(a.name)
}

private fun Counter<String>.countEvent(e: Event) {
    e.conceptName?.let { inc(it) }
}

internal class CountUnmatchedLogMovesInReplayModel(val model: ReplayModel) : CountUnmatchedLogMoves {

    override fun compute(
        startIndex: Int,
        trace: List<Event>,
        prevProcessState: ProcessModelState,
        curActivity: Activity?
    ): Int {
        prevProcessState as ReplayModelState

        val counter = Counter<String>()
        for (i in startIndex until trace.size)
            counter.countEvent(trace[i])
        for (i in prevProcessState.index + (if (curActivity !== null) 1 else 0) until model.trace.size)
            counter.countActivity(model.trace[i])
        return counter.values.sum()
    }

}