package processm.miners.heuristicminer

import processm.core.logging.logger
import processm.core.models.causalnet.*
import processm.core.models.causalnet.mock.Event
import processm.core.models.causalnet.verifier.State
import processm.miners.heuristicminer.longdistance.LongDistanceDependencyMiner
import processm.miners.heuristicminer.longdistance.NaiveLongDistanceDependencyMiner

typealias Trace = Sequence<Event>
typealias Log = Sequence<Trace>

internal infix fun <A, B> Collection<A>.times(right: Collection<B>): List<Pair<A, B>> {
    return this.flatMap { a -> right.map { b -> a to b } }
}


internal fun <T> allSubsets(prefix: Set<T>, rest: List<T>): Sequence<Set<T>> {
    if (rest.isEmpty())
        return sequenceOf(prefix)
    else {
        val n = rest.first()
        val newRest = rest.subList(1, rest.size)
        return allSubsets(prefix, newRest) + allSubsets(prefix + n, newRest)
    }
}

internal fun <T> Collection<T>.allSubsets(): Sequence<Set<T>> {
    return allSubsets(setOf(), this.toList())
}

class HeuristicMiner(
    private val log: Log,
    val minDirectlyFollows: Int = 1,
    val minDependency: Double = 1e-10,
    val minBindingSupport: Int = 1,
    val longDistanceDependencyMiner: LongDistanceDependencyMiner = NaiveLongDistanceDependencyMiner(),
    val splitSelector: BindingSelector<Split> = CountSeparately(minBindingSupport),
    val joinSelector: BindingSelector<Join> = CountSeparately(minBindingSupport),
    val hypothesisSelector: ReplayTraceHypothesisSelector = MostGreedyHypothesisSelector()
) {
    internal val directlyFollows: Counter<Pair<Node, Node>> by lazy {
        val result = Counter<Pair<Node, Node>>()
        log.forEach { trace ->
            println("TRACE: " + trace.map { e -> e.name }.toList())
            val i = trace.iterator()
            var prev = start
            while (i.hasNext()) {
                val curr = Node(i.next().name)
                result.inc(prev to curr)
                prev = curr
            }
            result.inc(prev to end)
        }
        result
    }

    internal val nodes: Set<Node> by lazy {
        (directlyFollows.keys.map { it.first } + directlyFollows.keys.map { it.second }).distinct().toSet()
    }

    internal val dependency: Map<Pair<Node, Node>, Double> by lazy {
        (nodes times nodes).associateWith { (a, b) ->
            if (a != b) {
                val ab = directlyFollows.getOrDefault(a to b, 0)
                val ba = directlyFollows.getOrDefault(b to a, 0)
                (ab - ba) / (ab + ba + 1.0)
            } else {
                val aa = directlyFollows.getOrDefault(a to a, 0)
                aa / (aa + 1.0)
            }
        }
    }


    private fun computeBindings(model: Model, trace: List<Node>): Pair<List<Split>, List<Join>> {
        var currentStates =
            sequenceOf(ReplayTrace(State(), listOf<Set<Pair<Node, Node>>>(), listOf<Set<Pair<Node, Node>>>()))
        for (currentNode in trace) {
            val consumable = model.incoming.getOrDefault(currentNode, setOf()).map { dep -> dep.source to dep.target }
            val producible = model.outgoing.getOrDefault(currentNode, setOf()).map { dep -> dep.source to dep.target }
            val knownJoins = model.joins[currentNode]
            val consumeCandidates: Sequence<Set<Pair<Node, Node>>> =
                if (knownJoins.isNullOrEmpty()) {
                    if (consumable.isNotEmpty())
                        consumable.allSubsets().filter { consume -> consume.isNotEmpty() }
                    else
                        sequenceOf(setOf())
                } else {
                    knownJoins.map { join -> join.sources.map { it to join.target }.toSet() }.asSequence()
                }
            val knownSplits = model.splits[currentNode]
            val produceCandidates: Sequence<Set<Pair<Node, Node>>> =
                if (knownSplits.isNullOrEmpty()) {
                    if (producible.isNotEmpty())
                        producible.allSubsets().filter { produce -> produce.isNotEmpty() }
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
            currentStates.forEach { (state, joins, splits) ->
                logger().trace("JOINS: " + joins.map { join -> join.map { (a, b) -> a.activity to b.activity } })
                logger().trace("SPLITS: " + splits.map { split -> split.map { (a, b) -> a.activity to b.activity } })
            }
        }
        if (!currentStates.any()) {
            return listOf<Split>() to listOf()
        }

        val (_, joins, splits) = hypothesisSelector(currentStates.toList())

        val finalSplits = splits.filter { split -> split.isNotEmpty() }
            .map { split -> Split(split.map { (a, b) -> Dependency(a, b) }.toSet()) }
        val finalJoins = joins.filter { join -> join.isNotEmpty() }
            .map { join -> Join(join.map { (a, b) -> Dependency(a, b) }.toSet()) }
        return finalSplits to finalJoins
    }

    private fun mineBindings(
        logWithNodes: Sequence<List<Node>>,
        model: MutableModel
    ) {
        joinSelector.reset()
        splitSelector.reset()
        logWithNodes.forEach { trace ->
            val (splits, joins) = computeBindings(model, trace)
            joinSelector.add(joins)
            splitSelector.add(splits)
        }
        joinSelector.best.forEach { join ->
            if (!model.contains(join))
                model.addJoin(join)
        }
        splitSelector.best.forEach { split ->
            if (!model.contains(split))
                model.addSplit(split)
        }
    }


    internal val start = Node("start", special = true)
    internal val end = Node("end", special = true)

    val result: MutableModel by lazy {
        val model = MutableModel(start = start, end = end)
        model.addInstance(*nodes.toTypedArray())
        directlyFollows
            .filterValues { it >= minDirectlyFollows }
            .keys
            .filter { k -> dependency.getOrDefault(k, 0.0) >= minDependency }
            .forEach { (a, b) -> model.addDependency(a, b) }
        val logWithNodes = log
            .map { trace -> listOf(start) + trace.map { e -> Node(e.name) }.toList() + listOf(end) }
        logWithNodes.forEach { trace ->
            longDistanceDependencyMiner.processTrace(trace)
        }
        mineBindings(logWithNodes, model)
        while (true) {
            val ltdeps = longDistanceDependencyMiner.mine(model)
            if (ltdeps.isNotEmpty()) {
                ltdeps.forEach { dep ->
                    model.addDependency(dep.first, dep.second)
                    model.clearBindingsFor(dep.first)
                    model.clearBindingsFor(dep.second)
                }
                mineBindings(logWithNodes, model)
            } else
                break
        }

        model
    }

}