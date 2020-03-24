package processm.miners.heuristicminer.bindingproviders

import processm.core.helpers.allSubsets
import processm.core.logging.logger
import processm.core.models.causalnet.*
import processm.core.verifiers.causalnet.State
import processm.miners.heuristicminer.ReplayTrace
import processm.miners.heuristicminer.bindingproviders.hypothesisselector.ReplayTraceHypothesisSelector

/**
 * A [BindingProvider] that exhaustively considers all possibilities and uses [hypothesisSelector] to select the best
 * bindings out of all valid executions of the given trace.
 *
 * In general far slower than [BestFirstBindingProvider], but in some cases may offer greater flexibility.
 */
class CompleteBindingProvider(val hypothesisSelector: ReplayTraceHypothesisSelector) : BindingProvider {

    override fun computeBindings(model: Model, trace: List<Node>): List<Binding> {
        var currentStates =
            sequenceOf(ReplayTrace(State(), listOf<Set<Pair<Node, Node>>>(), listOf<Set<Pair<Node, Node>>>()))
        for (currentNode in trace) {
            val consumable = model.incoming.getOrDefault(currentNode, setOf()).map { dep -> dep.source to dep.target }
            val producible = model.outgoing.getOrDefault(currentNode, setOf()).map { dep -> dep.source to dep.target }
            val knownJoins = model.joins[currentNode]
            val consumeCandidates: Sequence<Set<Pair<Node, Node>>> =
                if (knownJoins.isNullOrEmpty()) {
                    if (consumable.isNotEmpty())
                        consumable.allSubsets().filter { consume -> consume.isNotEmpty() }.map { it.toSet() }
                    else
                        sequenceOf(setOf())
                } else {
                    knownJoins.map { join -> join.sources.map { it to join.target }.toSet() }.asSequence()
                }
            val knownSplits = model.splits[currentNode]
            val produceCandidates: Sequence<Set<Pair<Node, Node>>> =
                if (knownSplits.isNullOrEmpty()) {
                    if (producible.isNotEmpty())
                        producible.allSubsets().filter { produce -> produce.isNotEmpty() }.map { it.toSet() }
                    else
                        sequenceOf(setOf())
                } else {
                    knownSplits.map { split -> split.targets.map { split.source to it }.toSet() }.asSequence()
                }
            // zjedz dowolny niepusty podzbiór consumable albo consumable jest puste
            // uzupełnij state o dowolny niepusty podzbiór producible albo producible jest puste
            currentStates = currentStates
                .flatMap { (state, joins, splits) ->
                    consumeCandidates
                        .filter { consume -> state.containsAll(consume) }
                        .flatMap { consume ->
                            produceCandidates
                                .map { produce ->
                                    val ns = State(state)
                                    ns.removeAll(consume)
                                    ns.addAll(produce)
                                    ReplayTrace(ns, joins + setOf(consume), splits + setOf(produce))
                                }
                        }
                }
        }
        currentStates = currentStates.filter { it.state.isEmpty() }
        if (logger().isTraceEnabled) {
            logger().trace("TRACE: " + trace.map { n -> n.activity })
            logger().trace(model.toString())
            currentStates.forEach { (state, joins, splits) ->
                logger().trace("JOINS: " + joins.map { join -> join.map { (a, b) -> a.activity to b.activity } })
                logger().trace("SPLITS: " + splits.map { split -> split.map { (a, b) -> a.activity to b.activity } })
            }
        }
        if (!currentStates.any()) {
            return listOf()
        }

        val (_, joins, splits) = hypothesisSelector(currentStates.toList())
        if (logger().isTraceEnabled) {
            logger().trace("WINNING JOINS: " + joins.map { join -> join.map { (a, b) -> a.activity to b.activity } })
            logger().trace("WINNING SPLITS: " + splits.map { split -> split.map { (a, b) -> a.activity to b.activity } })
        }

        val finalSplits = splits.filter { split -> split.isNotEmpty() }
            .map { split -> Split(split.map { (a, b) -> Dependency(a, b) }.toSet()) }
        val finalJoins = joins.filter { join -> join.isNotEmpty() }
            .map { join -> Join(join.map { (a, b) -> Dependency(a, b) }.toSet()) }
        return finalSplits + finalJoins
    }
}