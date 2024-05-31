package processm.experimental.performance

import processm.core.log.Event
import processm.core.models.causalnet.CausalNet
import processm.core.models.causalnet.CausalNetState
import processm.core.models.causalnet.Node
import processm.core.models.commons.Activity

class AlignmentStep(val event: Event?, val activity: Activity?, val stateBefore: CausalNetState)

data class AlignmentContext(
    val model: CausalNet,
    val events: List<Event>,
    val distance: Distance,
    val cheapEventAlignments: List<List<Pair<Node, Double>>>,
    val cheapNodeAlignments: Map<Node, List<Pair<Event, Double>>>
) {
    val aggregatedRemainders: List<Map<Event, Int>>

    init {
        val result = ArrayList<Map<Event, Int>>()
        val intermediary = HashMap<Event, Int>()
        for (e in events.reversed()) {
            intermediary.compute(e) { _, v ->
                (v ?: 0) + 1
            }
            result.add(HashMap(intermediary))
        }
        aggregatedRemainders = result.reversed()
    }
}

data class AlignmentState(val state: CausalNetState, val tracePosition: Int)

class Alignment(
    val cost: Double,
    val heuristic: Double,
    val alignment: Iterable<AlignmentStep>,
    internal val state: CausalNetState,
    internal val tracePosition: Int,
    internal val context: AlignmentContext,
    reachToTheFuture: Int,
    internal val minOccurrences:Map<Node,Int>
) {
    internal val features: List<Double>

    internal val alignmentState: AlignmentState
        get() = AlignmentState(state, tracePosition)

    /**
     * Cost must go first, otherwise the algorithm ceases to yield optimal alignments.
     */
    init {

        /*
        val nearest = if(localEvents!=null && a.tracePosition < localEvents.size) {
            val event = localEvents[a.tracePosition]
            model.available(a.state).map { dec -> distance(dec.activity, event) }.min()
                ?: Double.POSITIVE_INFINITY
        } else
            Double.POSITIVE_INFINITY

         */
//        features = listOf(cost, -tracePosition.toDouble(), -matching.toDouble())
        features = listOf(
            cost + heuristic,
            -tracePosition.toDouble(),
            //-nActivePossibleFutureActivities.toDouble(),
            reachToTheFuture.toDouble(),
            state.size().toDouble()
            /*,
            lazy {
                val matching =
                    if (tracePosition < context.events.size) {
                        val availableActivities = context.model.availableNodes(state).toList()
                        var ctr = 0
                        for (i in tracePosition until context.events.size) {
                            val e = context.events[i]
                            if (availableActivities.any { a -> context.distance(a, e) == 0.0 })
                                ctr++
                        }
                        ctr
                    } else 0
                -matching.toDouble()}*/
        )
        /*
        features = LazyList(3) { idx ->
            return@LazyList if (idx == 0) cost
            else if (idx == 1) -tracePosition.toDouble()
            else {

                -matching.toDouble()
            }
        }
         */

    }

}
