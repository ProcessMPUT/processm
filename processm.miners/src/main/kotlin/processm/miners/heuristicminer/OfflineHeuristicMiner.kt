package processm.miners.heuristicminer

import processm.core.log.hierarchical.Log
import processm.core.models.causalnet.Join
import processm.core.models.causalnet.MutableModel
import processm.core.models.causalnet.Node
import processm.core.models.causalnet.Split
import processm.miners.heuristicminer.bindingproviders.BestFirstBindingProvider
import processm.miners.heuristicminer.bindingproviders.BindingProvider
import processm.miners.heuristicminer.bindingselectors.BindingSelector
import processm.miners.heuristicminer.bindingselectors.CountSeparately
import processm.miners.heuristicminer.longdistance.LongDistanceDependencyMiner
import processm.miners.heuristicminer.longdistance.NaiveLongDistanceDependencyMiner

class OfflineHeuristicMiner(
    minDirectlyFollows: Int = 1,
    minDependency: Double = 1e-10,
    val minBindingSupport: Int = 1,
    val longDistanceDependencyMiner: LongDistanceDependencyMiner = NaiveLongDistanceDependencyMiner(),
    val splitSelector: BindingSelector<Split> = CountSeparately(
        minBindingSupport
    ),
    val joinSelector: BindingSelector<Join> = CountSeparately(
        minBindingSupport
    ),
    bindingProvider: BindingProvider = BestFirstBindingProvider()
) : AbstractHeuristicMiner(minDirectlyFollows, minDependency, bindingProvider) {
    private lateinit var log: Log

    override fun processLog(log: Log) {
        this.log = log
        for (trace in log.traces)
            updateDirectlyFollows(traceToNodeTrace(trace))
    }

    internal val nodes: Set<Node> by lazy {
        (directlyFollows.keys.map { it.first } + directlyFollows.keys.map { it.second }).distinct().toSet()
    }

    private fun mineBindings(
        logWithNodes: Sequence<List<Node>>,
        model: MutableModel
    ) {
        joinSelector.reset()
        splitSelector.reset()
        logWithNodes.forEach { trace ->
            val (joins, splits) = bindingProvider.computeBindings(model, trace).partition { it is Join }
            joinSelector.add(joins.map { it as Join })
            splitSelector.add(splits.map { it as Split })
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