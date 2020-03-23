package processm.miners.heuristicminer

import processm.core.helpers.allSubsets
import processm.core.log.Event
import processm.core.log.hierarchical.Trace
import processm.core.logging.logger
import processm.core.models.causalnet.*
import processm.core.verifiers.causalnet.State
import processm.miners.heuristicminer.hypothesisselector.ReplayTraceHypothesisSelector

internal typealias NodeTrace = List<Node>

abstract class AbstractHeuristicMiner(
    val minDirectlyFollows: Int,
    val minDependency: Double,
    val hypothesisSelector: ReplayTraceHypothesisSelector
) : HeuristicMiner {

    internal val start = Node("start", special = true)
    internal val end = Node("end", special = true)

    internal val directlyFollows = Counter<Pair<Node, Node>>()

    internal fun node(e: Event): Node {
        //TODO: do it right once appropriate interface is in place
        return Node(e.conceptName.toString(), e.conceptInstance ?: "")
    }

    protected fun traceToNodeTrace(trace: Trace): NodeTrace =
        trace.events.map { node(it) }.toList()

    protected fun updateDirectlyFollows(nodeTrace: NodeTrace) {
        val i = nodeTrace.iterator()
        var prev = start
        while (i.hasNext()) {
            val curr = i.next()
            directlyFollows.inc(prev to curr)
            prev = curr
        }
        directlyFollows.inc(prev to end)
    }

    internal fun dependency(a: Node, b: Node): Double {
        return if (a != b) {
            val ab = directlyFollows.getOrDefault(a to b, 0)
            val ba = directlyFollows.getOrDefault(b to a, 0)
            (ab - ba) / (ab + ba + 1.0)
        } else {
            val aa = directlyFollows.getOrDefault(a to a, 0)
            aa / (aa + 1.0)
        }
    }

    internal fun computeDependencyGraph() =
        directlyFollows
            .filterValues { it >= minDirectlyFollows }
            .keys
            .filter { (a, b) -> dependency(a, b) >= minDependency }

    internal fun computeBindings(model: Model, trace: List<Node>): List<Binding> {
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
            return listOf<Binding>()
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