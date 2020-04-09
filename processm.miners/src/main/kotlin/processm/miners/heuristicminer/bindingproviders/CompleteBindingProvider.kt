package processm.miners.heuristicminer.bindingproviders

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
class CompleteBindingProvider(val hypothesisSelector: ReplayTraceHypothesisSelector) : AbstractBindingProvider() {

    override fun computeBindings(model: Model, trace: List<Node>): List<Binding> {
        var currentStates =
            listOf(ReplayTrace(State(), listOf<Set<Dependency>>(), listOf<Set<Dependency>>()))
        for (idx in trace.indices) {
            val currentNode = trace[idx]
            val available = trace.subList(idx, trace.size).toSet()
            val produceCandidates = produceCandidates(model, currentNode, available)
            // zużyj dowolny niepusty podzbiór consumable albo consumable jest puste
            // uzupełnij state o dowolny niepusty podzbiór producible albo producible jest puste
            val nextStates = ArrayList<ReplayTrace>()
            for ((state, joins, splits) in currentStates) {
                for (consume in consumeCandidates(model, currentNode, state.uniqueSet())) {
                    if(state.containsAll(consume))
                    for (produce in produceCandidates) {
                        val ns = State(state)
                        ns.removeAll(consume)
                        ns.addAll(produce)

                        nextStates.add(ReplayTrace(ns, joins + setOf(consume), splits + setOf(produce)))
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