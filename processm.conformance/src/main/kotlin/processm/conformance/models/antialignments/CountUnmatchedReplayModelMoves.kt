package processm.conformance.models.antialignments

import processm.conformance.models.alignments.CountUnmatchedModelMoves
import processm.core.helpers.Counter
import processm.core.models.commons.ProcessModelState

internal class CountUnmatchedReplayModelMoves(val model: ReplayModel) : CountUnmatchedModelMoves {
    override fun compute(startIndex: Int, nEvents: List<Map<String?, Int>>, prevProcessState: ProcessModelState): Int {
        prevProcessState as ReplayModelState
        val nEvents = nEvents[startIndex]
        val remainingActivities = Counter<String>()
        for (i in prevProcessState.index until model.trace.size)
            remainingActivities.inc(model.trace[i].name)
        return remainingActivities.entries.sumOf { (act, count) ->
            val events = nEvents[act] ?: 0
            count - events
        }
    }
}
