package processm.miners.heuristicminer.bindingproviders

import processm.core.helpers.allSubsets
import processm.core.models.causalnet.*
import processm.core.verifiers.causalnet.State
import processm.miners.heuristicminer.NodeTrace
import processm.miners.heuristicminer.ReplayTrace
import java.util.*

data class ComputationState(val nextNode: Int, val trace: ReplayTrace, val nodeTrace: NodeTrace)

class DefaultComputationStateComparator : Comparator<ComputationState> {
    private fun value(o: ComputationState): IntArray {
        var targets = o.trace.state.map { it.second }.toSet()
        var nMissing =
            (o.nodeTrace.subList(o.nextNode, o.nodeTrace.size).toSet() - targets).size
        var nTargets = targets.size
        return intArrayOf(-nMissing, -nTargets, o.nextNode, -o.trace.state.size)
    }

    override fun compare(o1: ComputationState, o2: ComputationState): Int {
        for ((x, y) in value(o1) zip value(o2))
            if (x != y)
                return y - x
        return 0
    }
}


class BestFirstBindingProvider(
    val comparator: Comparator<ComputationState> = DefaultComputationStateComparator()
) : BindingProvider {

    override fun computeBindings(model: Model, trace: List<Node>): List<Binding> {
        val queue = PriorityQueue(comparator)
        queue.add(ComputationState(0, ReplayTrace(State(), listOf(), listOf()), trace))

        val consumeCandidates: List<Sequence<Set<Pair<Node, Node>>>> = trace.map { currentNode ->
            val consumable =
                model.incoming.getOrDefault(currentNode, setOf()).map { dep -> dep.source to dep.target }
            val knownJoins = model.joins[currentNode]
            if (knownJoins.isNullOrEmpty()) {
                if (consumable.isNotEmpty())
                    consumable.allSubsets().filter { it.isNotEmpty() }.map { it.toSet() }
                else
                    sequenceOf(setOf())
            } else {
                knownJoins.map { join -> join.sources.map { it to join.target }.toSet() }.asSequence()
            }
        }
        val produceCandidates: List<Sequence<Set<Pair<Node, Node>>>> = trace.map { currentNode ->
            val producible =
                model.outgoing.getOrDefault(currentNode, setOf()).map { dep -> dep.source to dep.target }
            val knownSplits = model.splits[currentNode]
            if (knownSplits.isNullOrEmpty()) {
                if (producible.isNotEmpty())
                    producible.allSubsets().filter { it.isNotEmpty() }.map { it.toSet() }
                else
                    sequenceOf(setOf())
            } else {
                knownSplits.map { split -> split.targets.map { split.source to it }.toSet() }.asSequence()
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
            val (state, joins, splits) = traceSoFar
            consumeCandidates[currentNodeIdx]
                .filter { consume -> state.containsAll(consume) }
                .flatMap { consume ->
                    produceCandidates[currentNodeIdx]
                        .map { produce ->
                            val ns = State(state)
                            ns.removeAll(consume)
                            ns.addAll(produce)
                            ReplayTrace(ns, joins + setOf(consume), splits + setOf(produce))
                        }
                }
                .forEach { queue.add(ComputationState(currentNodeIdx + 1, it, trace)) }
        }
        return emptyList()
    }
}