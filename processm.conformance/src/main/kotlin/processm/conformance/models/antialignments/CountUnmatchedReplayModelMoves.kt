package processm.conformance.models.antialignments

import processm.conformance.models.alignments.CountUnmatchedModelMoves
import processm.core.models.commons.Activity
import processm.core.models.commons.ProcessModelState
import processm.helpers.Counter

internal class CountUnmatchedReplayModelMoves(val model: ReplayModel) : CountUnmatchedModelMoves {
    override fun compute(
        startIndex: Int,
        nEvents: List<Map<String?, Int>>,
        prevProcessState: ProcessModelState,
        curActivity: Activity?
    ): Int {
        prevProcessState as ReplayModelState
        val nEvents = if (startIndex < nEvents.size) nEvents[startIndex] else emptyMap<String?, Int>()

        var counter = 0 // lower bound
        for (i in prevProcessState.index until model.trace.size) {
            val act = model.trace[i]
            if (i == prevProcessState.index && act == curActivity /*just executed activity*/)
                continue
            if (!act.isSilent && act.name !in nEvents)
                counter += 1
        }
        return counter

        // The below code calculates the exact value but is about 3 times slower; left for the reference
        // val remainingActivities = Counter<String>()
        // for (i in prevProcessState.index until model.trace.size)
        //     remainingActivities.inc(model.trace[i].name, 1)
        // return remainingActivities.entries.sumOf { (act, count) ->
        //     val events = nEvents[act] ?: 0
        //     count - events
        // }
    }
}
