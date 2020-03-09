package processm.miners.heuristicminer

import processm.core.logging.logger
import processm.core.models.causalnet.*
import processm.core.models.causalnet.mock.Event
import processm.core.models.causalnet.verifier.State

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
    val longTermDependencyMiner: LongTermDependencyMiner = NaiveLongTermDependencyMiner(),
    val splitSelector: BindingSelector<Split> = CountSeparately(minBindingSupport),
    val joinSelector: BindingSelector<Join> = CountSeparately(minBindingSupport)
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
            sequenceOf(Triple(State(), listOf<Set<Pair<Node, Node>>>(), listOf<Set<Pair<Node, Node>>>()))
        for (currentNode in trace) {
            val consumable = model.incoming.getOrDefault(currentNode, setOf()).map { dep -> dep.source to dep.target }
            val producable = model.outgoing.getOrDefault(currentNode, setOf()).map { dep -> dep.source to dep.target }
            // zjedz dowolny niepusty podzbiór consumable albo consumable jest puste
            if (!consumable.isEmpty())
                currentStates = currentStates
                    .flatMap { (state, joins, splits) ->
                        consumable.allSubsets()
                            .filter { consume -> !consume.isEmpty() }
                            .filter { consume -> state.containsAll(consume) }
                            .map { consume ->
                                val ns = State(state)
                                ns.removeAll(consume)
                                Triple(ns, joins + setOf(consume), splits)
                            }
                    }
            // uzupełnij state o dowolny niepusty podzbiór producable albo producable jest puste
            if (!producable.isEmpty())
                currentStates = currentStates
                    .flatMap { (state, joins, splits) ->
                        producable.allSubsets()
                            .filter { produce -> !produce.isEmpty() }
                            .map { produce ->
                                val ns = State(state)
                                ns.addAll(produce)
                                Triple(ns, joins, splits + setOf(produce))
                            }
                    }
        }
        currentStates = currentStates.filter { (state, joins, splits) -> state.isEmpty() }
        logger().trace("TRACE: " + trace.map { n -> n.activity })
        if (!currentStates.any()) {
            return listOf<Split>() to listOf()
        }
        if (logger().isTraceEnabled) {
            currentStates.forEach { (state, joins, splits) ->
                logger().trace("JOINS: " + joins.map { join -> join.map { (a, b) -> a.activity to b.activity } })
                logger().trace("SPLITS: " + splits.map { split -> split.map { (a, b) -> a.activity to b.activity } })
            }
        }
        //TODO badac zawieranie zamiast tego naiwnego sumowania
        val tmp = currentStates.map { (state, joins, splits) ->
            Triple(state, joins, splits) to joins.flatten().count() + splits.flatten().count()
        }
        val min = tmp.map { (k, v) -> v }.min()
        logger().trace("MIN SIZE: " + min)
        currentStates = tmp.filter { (k, v) -> v == min }.map { (k, v) -> k }
        logger().trace("CTR: " + currentStates.count())
        //TODO Co wlasciwie zrobic jezeli currentStates.count() > 1? Idea jest takie, żeby wybrać najbardziej oszczędną hipotezę odnośnie joinów/splitów, ale takich hipotez chyba może być kilka?
        assert(currentStates.count() == 1)
        val (_, joins, splits) = currentStates.single()
        val finalSplits = splits.map { split -> Split(split.map { (a, b) -> Dependency(a, b) }.toSet()) }
        val finalJoins = joins.map { join -> Join(join.map { (a, b) -> Dependency(a, b) }.toSet()) }
        return finalSplits to finalJoins
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
        log
            .map { trace ->
                longTermDependencyMiner.processTrace(trace)
                trace
            }
            .map { trace -> listOf(start) + trace.map { e -> Node(e.name) }.toList() + listOf(end) }
            .forEach { trace ->
                val (splits, joins) = computeBindings(model, trace)
                joinSelector.add(joins)
                splitSelector.add(splits)
            }
        joinSelector.best.forEach { join -> model.addJoin(join) }
        splitSelector.best.forEach { split -> model.addSplit(split) }
        while (true) {
            val ltdeps = longTermDependencyMiner.mine(model)
            if (ltdeps.isNotEmpty()) {
                ltdeps.forEach { dep ->
                    val dep = model.addDependency(dep.first, dep.second)
                    model.splits.values.flatten().forEach { split ->
                        if (split.source == dep.source) {
                            model.removeSplit(split)
                            model.addSplit(Split(split.dependencies + setOf(dep)))
                        }
                    }
                    model.joins.values.flatten().forEach { join ->
                        if (join.target == dep.target) {
                            model.removeJoin(join)
                            model.addJoin(Join(join.dependencies + setOf(dep)))
                        }
                    }
                }
            } else
                break
        }

        model
    }
}