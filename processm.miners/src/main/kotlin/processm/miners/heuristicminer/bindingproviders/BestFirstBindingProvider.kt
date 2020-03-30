package processm.miners.heuristicminer.bindingproviders

import processm.core.helpers.allSubsets
import processm.core.logging.logger
import processm.core.models.causalnet.*
import processm.core.verifiers.causalnet.State
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
data class ComputationState(val nextNode: Int, val trace: ReplayTrace, val nodeTrace: NodeTrace)

/**
 * Compares two [ComputationState] for the purposes of maintaining a priority queue.
 *
 * The following criterion are considered
 * 1. Number of nodes that are yet to be visited, but are not present in the current state (fewer is beter).
 * 2. Minimal number of nodes that are yet to be visited according to the current state (fewer is better).
 * 3. Number of pending obligations in the state (fewer is better).
 *
 * The corresponding experimental evaluation is in [DefaultComputationStateComparatorPerformanceTest]
 */
class DefaultComputationStateComparator : Comparator<ComputationState> {
    private fun value(o: ComputationState): IntArray {
        val targets = o.trace.state.map { it.target }.toSet()
        val nMissing =
            (o.nodeTrace.subList(o.nextNode, o.nodeTrace.size).toSet() - targets).size
        val nTargets = targets.size
        return intArrayOf(-nMissing, -nTargets, -o.trace.state.size)
    }

    override fun compare(o1: ComputationState, o2: ComputationState): Int {
        for ((x, y) in value(o1) zip value(o2))
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
    val comparator: Comparator<ComputationState> = DefaultComputationStateComparator()
) : BindingProvider {

    override fun computeBindings(model: Model, trace: List<Node>): List<Binding> {
        val queue = PriorityQueue(comparator)
        queue.add(ComputationState(0, ReplayTrace(State(), listOf(), listOf()), trace))

        val consumeCandidates: List<Sequence<Set<Dependency>>> = trace.map { currentNode ->
            val consumable =
                model.incoming.getOrDefault(currentNode, setOf())
            val knownJoins = model.joins[currentNode]
            if (knownJoins.isNullOrEmpty()) {
                if (consumable.isNotEmpty())
                    consumable.allSubsets().filter { it.isNotEmpty() }.map { it.toSet() }
                else
                    sequenceOf(setOf())
            } else {
                knownJoins.map { join -> join.dependencies }.asSequence()
            }
        }
        val produceCandidates: List<Sequence<Set<Dependency>>> = trace.map { currentNode ->
            val producible =
                model.outgoing.getOrDefault(currentNode, setOf())
            val knownSplits = model.splits[currentNode]
            if (knownSplits.isNullOrEmpty()) {
                if (producible.isNotEmpty())
                    producible.allSubsets().filter { it.isNotEmpty() }.map { it.toSet() }
                else
                    sequenceOf(setOf())
            } else {
                knownSplits.map { split -> split.dependencies }.asSequence()
            }
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
            consumeCandidates[currentNodeIdx]
                .filter { consume -> traceSoFar.state.containsAll(consume) }
                .flatMap { consume ->
                    produceCandidates[currentNodeIdx]
                        .map { produce ->
                            val ns = State(traceSoFar.state)
                            ns.removeAll(consume)
                            ns.addAll(produce)
                            ReplayTrace(ns, traceSoFar.joins + setOf(consume), traceSoFar.splits + setOf(produce))
                        }
                }
                .forEach { queue.add(ComputationState(currentNodeIdx + 1, it, trace)) }
        }
        logger().warn("Failed to compute bindings for $trace")
        logger().warn(model.toString())
        return emptyList()
    }
}