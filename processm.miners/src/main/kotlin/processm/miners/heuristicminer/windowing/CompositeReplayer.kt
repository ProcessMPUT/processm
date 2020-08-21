package processm.miners.heuristicminer.windowing

import com.google.common.collect.MinMaxPriorityQueue
import org.apache.commons.lang3.math.Fraction
import processm.core.helpers.HierarchicalIterable
import processm.core.helpers.allSubsets
import processm.core.helpers.mapToSet
import processm.core.logging.trace
import processm.core.models.causalnet.*
import processm.miners.heuristicminer.NodeTrace
import processm.miners.heuristicminer.ReplayTrace
import kotlin.math.min
import processm.core.logging.logger

class CompositeReplayer(val horizon:Int=-1):Replayer {

    companion object {
        private val logger=logger()
    }

    override fun replayGroup(model: CausalNet, traces: List<NodeTrace>): Pair<Set<Split>, Set<Join>> {
        val producible = traces.map { trace ->
            trace.indices.map { idx ->
                val end = if (horizon > 0) min(idx + 1 + horizon, trace.size) else trace.size
                model.outgoing[trace[idx]].orEmpty()
                    .intersect(trace.subList(idx + 1, end).map { Dependency(trace[idx], it) })
            }
        }
        val seen = HashSet<List<ReplayTrace>>()
        val allActivities = traces.flatten().toSet()
        val queue = MinMaxPriorityQueue.create<CompositeSearchState>()
        queue.add(
            CompositeSearchState(
                0,
                0,
                emptySet(),
                emptySet(),
                emptySet(),
                emptySet(),
                allActivities,
                traces.map { trace ->
                    SearchState(
                        Fraction.ZERO,
                        Fraction.ZERO,
                        0,
                        0,
                        true,
                        ReplayTrace(
                            CausalNetStateImpl(),
                            listOf(),
                            listOf()
                        )
                    )
                },
                traces
            )
        )
        var ctr = 0
        var skipCtr = 0
        while (!queue.isEmpty()) {
            val currentComposite = queue.pollFirst()
            /*
            println(currentComposite.base.map {
                val n = if(it.node < it.nodeTrace.size) it.nodeTrace[it.node] else null
                "$n state=${it.trace.state} joins=${it.trace.joins.toList()} splits=${it.trace.splits.toList()}\n"
            })
             */
            /*
            val replayTraces = currentComposite.base.map { it.trace }
            if (seen.contains(replayTraces)) {
                skipCtr++
                continue
            }
            seen.add(replayTraces)
             */
            ctr++
            if (ctr % 10000 == 0)
                logger.info ( "ctr=$ctr queue=${queue.size} ${currentComposite.features}" )
            var anyFailed = false
            var allFinished = true
            for ((traceIdx, current) in currentComposite.base.withIndex()) {
                if (current.produce && current.node == traces[traceIdx].size - 1) {
                    if (current.trace.state.isNotEmpty()) {
                        //this is an invalid solution without any chances of improvement
                        anyFailed = true
                        break
                    }
                } else {
                    if (!current.produce && current.trace.state.isEmpty())
                        anyFailed = true
                    allFinished = false
                }
            }
            if (anyFailed)
                continue
            if (allFinished) {
                val splits =
                    currentComposite.base.flatMapTo(HashSet()) { current -> current.trace.splits.map { Split(it.toSet()) } }
                val joins =
                    currentComposite.base.flatMapTo(HashSet()) { current -> current.trace.joins.map { Join(it.toSet()) } }
                logger.debug("Number of states visited $ctr, skipped $skipCtr")
                return splits to joins
            }
            logger.trace { "${currentComposite.parent}/$ctr ${currentComposite.position} features = ${currentComposite.features}" }
            var currentIdx = currentComposite.position
            //skip traces that finished successfully
            while (currentComposite.base[currentIdx].node == traces[currentIdx].size - 1 && currentComposite.base[currentIdx].produce) {
                currentIdx = (currentIdx + 1) % currentComposite.base.size
            }
            val current = currentComposite.base[currentIdx]
            val trace = traces[currentIdx]
            val currentNode = trace[current.node]
            val newLength = current.solutionLength + 1
            val newSearchStates = ArrayList<SearchState>()
            if (current.produce) {
                for (produce in producible[currentIdx][current.node].allSubsets(true)) {
                    val newState = CausalNetStateImpl(current.trace.state)
                    newState.addAll(produce)
                    val newValue =
                        current.totalGreediness + Fraction.getFraction(produce.size,  producible[currentIdx][current.node].size)
                    val newReplayTrace =
                        ReplayTrace(
                            newState,
                            current.trace.joins,
                            HierarchicalIterable(current.trace.splits, produce)
                        )
                    newSearchStates.add(
                        SearchState(
                            newValue,
                            Fraction.ZERO,
                            newLength,
                            current.node + 1,
                            false,
                            newReplayTrace
                        )
                    )
                }
            } else {
                val consumable = current.trace.state.uniqueSet().intersect(model.incoming.getValue(currentNode))
                for (consume in consumable.allSubsets(true)) {
                    val newState = CausalNetStateImpl(current.trace.state)
                    for (c in consume)
                        newState.remove(c, 1)
                    val newValue = current.totalGreediness + Fraction.getFraction(consume.size, consumable.size)
                    val newReplayTrace =
                        ReplayTrace(
                            newState,
                            HierarchicalIterable(current.trace.joins, consume),
                            current.trace.splits
                        )
                    newSearchStates.add(
                        SearchState(
                            newValue,
                            Fraction.ZERO,
                            newLength,
                            current.node,
                            true,
                            newReplayTrace
                        )
                    )
                }
            }
            val next = (currentIdx + 1) % currentComposite.base.size
            if (newSearchStates.isNotEmpty()) {
                val left = currentComposite.base.subList(0, currentIdx)
                val right = currentComposite.base.subList(currentIdx + 1, currentComposite.base.size)
                assert(left.size + right.size == currentComposite.base.size - 1)
                for (ss in newSearchStates) {
                    val joins = lazyAdd(currentComposite.joins, last(ss.trace.joins))
                    val splits = lazyAdd(currentComposite.splits, last(ss.trace.splits))
                    val havingSomeJoin = lazyAdd(currentComposite.havingSomeJoin, last(ss.trace.joins).mapToSet { it.first().target })
                    val havingSomeSplit = lazyAdd(currentComposite.havingSomeSplit, last(ss.trace.splits).mapToSet { it.first().source })
                    queue.add(
                        CompositeSearchState(
                            next,
                            ctr,
                            joins,
                            splits,
                            havingSomeJoin,
                            havingSomeSplit,
                            allActivities,
                            ChainingList(
                                left,
                                ss,
                                right
                            ),
                            traces
                        )
                    )
                }
            }
        }
        throw IllegalStateException()
    }

    private fun <T> lazyAdd(left: Set<T>, right: Set<T>): Set<T> =
        if (left.containsAll(right))
            left
        else
            left + right

    private fun <T> last(i: Iterable<T>): Set<T> {
        if (i is HierarchicalIterable)
            return setOf(i.suffix)
        if (i is List)
            if (i.isEmpty())
                return emptySet()
            else
                return setOf(i.last())
        throw IllegalArgumentException()
    }
}