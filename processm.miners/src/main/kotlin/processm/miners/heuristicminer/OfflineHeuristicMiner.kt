package processm.miners.heuristicminer

import processm.core.log.Event
import processm.core.log.hierarchical.Log
import processm.core.logging.logger
import processm.core.models.causalnet.*
import processm.core.verifiers.causalnet.State
import processm.miners.heuristicminer.hypothesisselector.MostGreedyHypothesisSelector
import processm.miners.heuristicminer.hypothesisselector.ReplayTraceHypothesisSelector
import processm.miners.heuristicminer.longdistance.LongDistanceDependencyMiner
import processm.miners.heuristicminer.longdistance.NaiveLongDistanceDependencyMiner

class OfflineHeuristicMiner(
    minDirectlyFollows: Int = 1,
    minDependency: Double = 1e-10,
    val minBindingSupport: Int = 1,
    val longDistanceDependencyMiner: LongDistanceDependencyMiner = NaiveLongDistanceDependencyMiner(),
    val splitSelector: BindingSelector<Split> = CountSeparately(minBindingSupport),
    val joinSelector: BindingSelector<Join> = CountSeparately(minBindingSupport),
    val hypothesisSelector: ReplayTraceHypothesisSelector = MostGreedyHypothesisSelector()
) : AbstractHeuristicMiner(minDirectlyFollows, minDependency) {
    private lateinit var log: Log

    override fun processLog(log: Log) {
        this.log = log
        for (trace in log.traces)
            updateDirectlyFollows(traceToNodeTrace(trace))
    }

    internal val nodes: Set<Node> by lazy {
        (directlyFollows.keys.map { it.first } + directlyFollows.keys.map { it.second }).distinct().toSet()
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


    override val result: MutableModel by lazy {
        val model = MutableModel(start = start, end = end)
        model.addInstance(*nodes.toTypedArray())
        for ((a, b) in computeDependencyGraph())
            model.addDependency(a, b)
        val logWithNodes = log.traces
            .map { trace -> listOf(start) + trace.events.map { e -> node(e) }.toList() + listOf(end) }
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