package processm.miners.causalnet.heuristicminer.bindingproviders

import com.google.common.collect.MinMaxPriorityQueue
import processm.core.models.causalnet.*
import processm.helpers.HierarchicalIterable
import processm.helpers.asList
import processm.helpers.mapToSet
import processm.logging.logger
import processm.miners.causalnet.onlineminer.LazyCausalNetState
import processm.miners.causalnet.onlineminer.NodeTrace
import processm.miners.causalnet.onlineminer.replayer.ReplayTrace
import java.util.*

/**
 * Represents a node in a search graph of [BestFirstBindingProvider]
 *
 * @param nextNode Index of the node in [nodeTrace] to be considered next
 * @param trace States, joins and splits so far
 * @param nodeTrace Complete trace being considered
 */
data class ComputationState(val nextNode: Int, val trace: ReplayTrace, val nodeTrace: NodeTrace) {

    /**
     * Priority in a priority queue. The following is considered:
     * 1. Minimal number of nodes that are yet to be visited according to the current state (fewer is better).
     * 2. Position in trace to be considered next (higher is better).
     *
     * The corresponding experimental evaluation is in [DefaultComputationStateComparatorPerformanceTest]
     */
    val value: IntArray by lazy {
        val nTargets = trace.state.uniqueSize
        return@lazy intArrayOf(-nTargets, nextNode)
    }
}

class DefaultComputationStateComparator : Comparator<ComputationState> {
    override fun compare(o1: ComputationState, o2: ComputationState): Int {
        for ((x, y) in o1.value zip o2.value)
            if (x != y)
                return y - x
        return 0
    }
}

/**
 * Computes bindings for a given trace in a give model using best-first principle, where best is defined by [comparator].
 *
 * [BestFirstBindingProvider] maintains a priority queue of partial [ReplayTrace]s and, at each step, considers the best
 * currently available. Once it reaches any correct and complete replay trace, it immediately stops computation and returns it.
 */
class BestFirstBindingProvider(
    val comparator: Comparator<ComputationState> = DefaultComputationStateComparator(),
    val maxQueueSize: Int = 1000000
) :
    AbstractBindingProvider() {

    companion object {
        private val logger = logger()
    }

    private fun maxSetSize(n: Int): Int {
        var s = 0.0
        var p = 1.0
        for (k in 1..n) {
            p *= (n - (k - 1)).toDouble() / k
            s += p
            if (s > maxQueueSize)
                return k
        }
        return n
    }

    override fun computeBindings(model: CausalNet, trace: List<Node>): List<Binding> {
        //val queue = PriorityQueue(comparator)
        val queue = MinMaxPriorityQueue.orderedBy(comparator).maximumSize(maxQueueSize).create<ComputationState>()
        queue.add(ComputationState(0, ReplayTrace(CausalNetStateImpl(), listOf(), listOf()), trace))

//        val visited = HashSet<Pair<Int, CausalNetStateImpl>>()
        val available = HashSet<Node>()
        val produceCandidates: MutableList<Iterable<Collection<Dependency>>> =
            MutableList(trace.size) { emptyList<Collection<Dependency>>() }
        val availableAt = MutableList(trace.size) { emptySet<Node>() }
        for (idx in trace.indices.reversed()) {
            val currentNode = trace[idx]
            val size = maxSetSize(available.size)
            //logger.debug { "idx=$idx #available=${available.size} maxQueueSize=$maxQueueSize maxSetSize=$size" }
            produceCandidates[idx] = produceCandidates(model, currentNode, available, size)
            //logger.debug { "${produceCandidates[idx].count()}" }
            availableAt[idx] = HashSet(available)
            available.add(currentNode)
        }
//        check(trace.size<=11 || produceCandidates[11].count()>0)
//        var printCtr = 0
//        var skipCtr = 0
        while (queue.isNotEmpty()) {
//            printCtr++
//            if (printCtr == 100) {
//                logger.debug { "queue size ${queue.size}" }
//                printCtr = 0
//            }
            val compState = queue.poll()!!
            val (currentNodeIdx, traceSoFar) = compState
            val copy = CausalNetStateImpl(traceSoFar.state)
            if (currentNodeIdx == trace.size) {
                if (copy.isEmpty()) {
                    val (_, joins, splits) = traceSoFar
                    val finalSplits = splits.filter { split -> split.isNotEmpty() }
                        .map { split -> Split(split.mapToSet { (a, b) -> Dependency(a, b) }) }
                    val finalJoins = joins.filter { join -> join.isNotEmpty() }
                        .map { join -> Join(join.mapToSet { (a, b) -> Dependency(a, b) }) }
//                    logger.debug("skipCtr=$skipCtr")
                    return finalSplits + finalJoins
                }
                continue
            }
//            val key = currentNodeIdx to copy
//            if (visited.contains(key)) {
//                //skipCtr++
//                continue
//            }
//            visited.add(key)
            val avail = copy.uniqueSet().asList()
//            val alreadyProduced = avail.mapToSet { it.target }
//            val mustProduceNodes = availableAt[currentNodeIdx].intersect(alreadyProduced)
            val currentNode = trace[currentNodeIdx]
//            val mustProduce = model.outgoing[currentNode].orEmpty().filter { it.target in mustProduceNodes }
            //           val availableForProduction = availableAt[currentNodeIdx] //- (alreadyProduced-currentNode)
//            logger.debug {"available ${availableAt[currentNodeIdx]} produced ${alreadyProduced} for production ${availableForProduction} must produce $mustProduce"}
            //logger.debug("currentNodeIdx=$currentNodeIdx")
            //logger.debug("Max consume size: $size, avail.size=${avail.size} produce ${produceCandidates[currentNodeIdx].count()}")
            for (consume in consumeCandidates(model, currentNode, avail)) {
                if (copy.containsAll(consume)) {
                    for (produce in produceCandidates[currentNodeIdx]) {
                        val it = ReplayTrace(
                            LazyCausalNetState(traceSoFar.state, consume, produce),
                            HierarchicalIterable(traceSoFar.joins, consume),
                            HierarchicalIterable(traceSoFar.splits, produce)
                        )
                        queue.add(ComputationState(currentNodeIdx + 1, it, trace))
                    }
                }
            }
        }
        logger.warn("Failed to compute bindings for $trace")
//        logger.warn(model.toString())
        return emptyList()
    }
}
