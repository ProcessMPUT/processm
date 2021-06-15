package processm.miners.causalnet.heuristicminer.bindingproviders

import processm.core.helpers.HierarchicalIterable
import processm.core.helpers.mapToSet
import processm.core.logging.logger
import processm.core.models.causalnet.*
import processm.miners.causalnet.heuristicminer.bindingproviders.hypothesisselector.ReplayTraceHypothesisSelector
import processm.miners.causalnet.onlineminer.replayer.ReplayTrace

/**
 * A [BindingProvider] that exhaustively considers all possibilities and uses [hypothesisSelector] to select the best
 * bindings out of all valid executions of the given trace.
 *
 * In general far slower than [BestFirstBindingProvider], but in some cases may offer greater flexibility.
 */
class CompleteBindingProvider(val hypothesisSelector: ReplayTraceHypothesisSelector) : AbstractBindingProvider() {

    override fun computeBindings(model: CausalNet, trace: List<Node>): List<Binding> {
        var currentStates =
            listOf(ReplayTrace(CausalNetStateImpl(), listOf<Set<Dependency>>(), listOf<Set<Dependency>>()))
        for (idx in trace.indices) {
            val currentNode = trace[idx]
            val remainder = trace.subList(idx + 1, trace.size).toSet()
            val available = remainder + currentNode
            val produceCandidates = produceCandidates(model, currentNode, available)
            // zużyj dowolny niepusty podzbiór consumable albo consumable jest puste
            // uzupełnij state o dowolny niepusty podzbiór producible albo producible jest puste
            val nextStates = ArrayList<ReplayTrace>()
            for ((state, joins, splits) in currentStates) {
                for (consume in consumeCandidates(model, currentNode, state.uniqueSet())) {
                    assert(state.containsAll(consume))
                    val intermediate = CausalNetStateImpl(state)
                    for (c in consume)
                        intermediate.remove(c)
                    for (produce in produceCandidates) {
                        val ns = CausalNetStateImpl(intermediate)
                        ns.addAll(produce)

                        if (!remainder.containsAll(ns.mapToSet { it.target }))
                            continue
                        nextStates.add(
                            ReplayTrace(
                                ns,
                                HierarchicalIterable(joins, consume),
                                HierarchicalIterable(splits, produce)
                            )
                        )
                    }
                }
            }
            currentStates = nextStates
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

        val (_, joins, splits) = hypothesisSelector(currentStates)
        if (logger().isTraceEnabled) {
            logger().trace("WINNING JOINS: " + joins.map { join -> join.map { (a, b) -> a.activity to b.activity } })
            logger().trace("WINNING SPLITS: " + splits.map { split -> split.map { (a, b) -> a.activity to b.activity } })
        }

        val finalSplits = splits.filter { split -> split.isNotEmpty() }
            .map { split -> Split(split.mapToSet { (a, b) -> Dependency(a, b) }) }
        val finalJoins = joins.filter { join -> join.isNotEmpty() }
            .map { join -> Join(join.mapToSet { (a, b) -> Dependency(a, b) }) }
        return finalSplits + finalJoins
    }
}