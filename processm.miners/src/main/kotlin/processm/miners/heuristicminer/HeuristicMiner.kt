package processm.miners.heuristicminer

import processm.core.log.Event
import processm.core.log.hierarchical.Log
import processm.core.log.hierarchical.Trace
import processm.core.logging.logger
import processm.core.models.causalnet.*
import processm.core.verifiers.causalnet.State
import processm.miners.heuristicminer.hypothesisselector.MostGreedyHypothesisSelector
import processm.miners.heuristicminer.hypothesisselector.ReplayTraceHypothesisSelector
import processm.miners.heuristicminer.longdistance.LongDistanceDependencyMiner
import processm.miners.heuristicminer.longdistance.NaiveLongDistanceDependencyMiner

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

internal fun node(e: Event): Node {
    //TODO: do it right once appropriate interface is in place
    return Node(e.conceptName.toString(), e.conceptInstance ?: "")
}

class HeuristicMiner(
    val minDirectlyFollows: Int = 1,
    val minDependency: Double = 1e-10,
    val minBindingSupport: Int = 1,
    val longDistanceDependencyMiner: LongDistanceDependencyMiner = NaiveLongDistanceDependencyMiner(),
    val hypothesisSelector: ReplayTraceHypothesisSelector = MostGreedyHypothesisSelector()
) {

    init {
//        (logger() as Logger).level = Level.TRACE
    }

    fun processLog(log: Log) {
        for (trace in log.traces)
            processTrace(trace)
    }

    fun processTrace(trace: Trace) {
        //Directly follows
        val nodeTrace = trace.events.map { node(it) }.toList()
        val i = nodeTrace.iterator()
        var prev = start
        while (i.hasNext()) {
            val curr = i.next()
            directlyFollows.inc(prev to curr)
            prev = curr
        }
        directlyFollows.inc(prev to end)
        model.addInstance(*(nodeTrace.toSet() - model.instances).toTypedArray())
        //TODO consider only pairs (in both directions!) from the trace and add/remove accordingly
        model.clearDependencies()
        directlyFollows
            .filterValues { it >= minDirectlyFollows }
            .keys
            .filter { (a, b) -> dependency(a, b) >= minDependency }
            .forEach { (a, b) -> model.addDependency(a, b) }

        model.clearBindings()
        println("TRACE " + (nodeTrace.map { it.activity }.toString()))
        mineBindings(nodeTrace)
        println(model)
//        longDistanceDependencyMiner.processTrace(nodeTrace)
//        while (true) {
//            val ltdeps = longDistanceDependencyMiner.mine(model)
//            if (ltdeps.isNotEmpty()) {
//                ltdeps.forEach { dep ->
//                    model.addDependency(dep.first, dep.second)
//                    model.clearBindingsFor(dep.first)
//                    model.clearBindingsFor(dep.second)
//                }
//                mineBindings(nodeTrace)
//            } else
//                break
//        }
    }

    internal val directlyFollows = Counter<Pair<Node, Node>>()


    internal fun dependency(a: Node, b: Node): Double {
        if (a != b) {
            val ab = directlyFollows.getOrDefault(a to b, 0)
            val ba = directlyFollows.getOrDefault(b to a, 0)
            return (ab - ba) / (ab + ba + 1.0)
        } else {
            val aa = directlyFollows.getOrDefault(a to a, 0)
            return aa / (aa + 1.0)
        }
    }

    private fun computeBindings(trace: List<Node>): List<Binding> {
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

    private var unableToReplay = ArrayList<List<Node>>()

    class HashMapWithDefault<K, V>(private val default: () -> V) : HashMap<K, V>() {
        override operator fun get(key: K): V {
            val result = super.get(key)
            if (result == null) {
                val new = default()
                this[key] = new
                return new
            } else {
                return result
            }
        }
    }

    protected val bindingCounter = HashMapWithDefault<Binding, HashSet<List<Node>>> { HashSet() }


    private fun mineBindings(nodeTrace: List<Node>) {
        val bindings = computeBindings(listOf(start) + nodeTrace + listOf(end))
        if (bindings.isNotEmpty()) {
            for (binding in bindings)
                bindingCounter[binding].add(nodeTrace)
            var bestBindings = bindingCounter.filterValues { it.size >= minBindingSupport }.keys
            val availableDependencies = (model.outgoing.values.flatten() + model.incoming.values.flatten()).toSet()
            val toReplay = bestBindings
                .filter { !availableDependencies.containsAll(it.dependencies) }
                .flatMap { bindingCounter[it] }
                .toSet()
            bindingCounter.values.forEach { it.removeAll(toReplay) }
            if (toReplay.isNotEmpty()) {
                unableToReplay.addAll(toReplay)
                val i = unableToReplay.iterator()
                while (i.hasNext()) {
                    val trace = i.next()
                    val bindings = computeBindings(listOf(start) + trace + listOf(end))
                    if (bindings.isNotEmpty()) {
                        for (binding in bindings)
                            bindingCounter[binding].add(trace)
                        i.remove()
                    }
                }
                bestBindings = bindingCounter.filterValues { it.size >= minBindingSupport }.keys
            }
            assert(bestBindings.all { availableDependencies.containsAll(it.dependencies) })
            for (binding in bestBindings)
                if (binding is Split)
                    model.addSplit(binding)
                else
                    model.addJoin(binding as Join)
        } else {
            unableToReplay.add(nodeTrace)
        }
    }


//        val logWithNodes = log.traces
//            .map { trace -> listOf(start) + trace.events.map { e -> node(e) }.toList() + listOf(end) }
//        logWithNodes.forEach { trace ->
//            longDistanceDependencyMiner.processTrace(trace)
//        }
//        mineBindings(logWithNodes, model)
//        while (true) {
//            val ltdeps = longDistanceDependencyMiner.mine(model)
//            if (ltdeps.isNotEmpty()) {
//                ltdeps.forEach { dep ->
//                    model.addDependency(dep.first, dep.second)
//                    model.clearBindingsFor(dep.first)
//                    model.clearBindingsFor(dep.second)
//                }
//                mineBindings(logWithNodes, model)
//            } else
//                break
//        }


    private val model = MutableModel()

    internal val start = model.start
    internal val end = model.end

    val result: Model = model


}