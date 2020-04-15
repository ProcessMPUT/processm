package processm.miners.heuristicminer.bindingproviders

import processm.core.helpers.HierarchicalIterable
import processm.core.logging.logger
import processm.core.models.causalnet.*
import processm.miners.heuristicminer.NodeTrace
import processm.miners.heuristicminer.ReplayTrace
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
        val nTargets = trace.state.uniqueSet().size
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
class BestFirstBindingProvider(val comparator: Comparator<ComputationState> = DefaultComputationStateComparator()) :
    AbstractBindingProvider() {

    override fun computeBindings(model: CausalNet, trace: List<Node>): List<Binding> {
        val queue = PriorityQueue(comparator)
        queue.add(ComputationState(0, ReplayTrace(CausalNetState(), listOf(), listOf()), trace))

        val available = HashSet<Node>()
        val produceCandidates: MutableList<Iterable<Collection<Dependency>>> =
            MutableList(trace.size) { emptyList<Collection<Dependency>>() }
        for (idx in trace.indices.reversed()) {
            val currentNode = trace[idx]
            produceCandidates[idx] = produceCandidates(model, currentNode, available)
            available.add(currentNode)
        }
        while (queue.isNotEmpty()) {
            val compState = queue.poll()!!
            val (currentNodeIdx, traceSoFar) = compState
            if (currentNodeIdx == trace.size) {
                if (traceSoFar.state.isEmpty()) {
                    val (_, joins, splits) = traceSoFar
                    val finalSplits = splits.filter { split -> split.isNotEmpty() }
                        .map { split -> Split(split.map { (a, b) -> Dependency(a, b) }.toSet()) }
                    val finalJoins = joins.filter { join -> join.isNotEmpty() }
                        .map { join -> Join(join.map { (a, b) -> Dependency(a, b) }.toSet()) }
                    return finalSplits + finalJoins
                }
                continue
            }
            val avail = traceSoFar.state.uniqueSet()
            for (consume in consumeCandidates(model, trace[currentNodeIdx], avail)) {
                if (traceSoFar.state.containsAll(consume)) {
                    val intermediate = CausalNetState(traceSoFar.state)
                    for (c in consume)
                        intermediate.remove(c)
                    for (produce in produceCandidates[currentNodeIdx]) {
                        val ns = CausalNetState(intermediate)
                        ns.addAll(produce)
                        val it = ReplayTrace(
                            ns,
                            HierarchicalIterable(traceSoFar.joins, consume),
                            HierarchicalIterable(traceSoFar.splits, produce)
                        )
                        queue.add(ComputationState(currentNodeIdx + 1, it, trace))
                    }
                }
            }
        }
        logger().warn("Failed to compute bindings for $trace")
        logger().warn(model.toString())
        return emptyList()
    }
}