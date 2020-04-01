package processm.miners.heuristicminer

import ch.qos.logback.classic.Level
import processm.core.log.hierarchical.Log
import processm.core.logging.logger
import processm.core.models.causalnet.*
import processm.miners.heuristicminer.bindingproviders.BestFirstBindingProvider
import processm.miners.heuristicminer.bindingproviders.BindingProvider
import processm.miners.heuristicminer.bindingselectors.BindingSelector
import processm.miners.heuristicminer.bindingselectors.CountSeparately
import processm.miners.heuristicminer.dependencygraphproviders.DefaultDependencyGraphProvider
import processm.miners.heuristicminer.dependencygraphproviders.DependencyGraphProvider
import processm.miners.heuristicminer.longdistance.LongDistanceDependencyMiner
import processm.miners.heuristicminer.longdistance.NaiveLongDistanceDependencyMiner
import java.util.*
import kotlin.collections.HashMap

/**
 * An off-line implementation of Heuristic Miner.
 *
 * It is more efficient than [OnlineHeuristicMiner], as it does not need to recompute the model after each trace.
 * For the default values of parameters, the final model is guaranteed to have fitness = 1 for each of the presented traces.
 */
class OfflineHeuristicMiner(
    val traceToNodeTrace: TraceToNodeTrace = BasicTraceToNodeTrace(),
    val dependencyGraphProvider: DependencyGraphProvider = DefaultDependencyGraphProvider(1, 1e-10),
    val longDistanceDependencyMiner: LongDistanceDependencyMiner = NaiveLongDistanceDependencyMiner(),
    val splitSelector: BindingSelector<Split> = CountSeparately(1),
    val joinSelector: BindingSelector<Join> = CountSeparately(1),
    val bindingProvider: BindingProvider = BestFirstBindingProvider()
) : HeuristicMiner {
    private lateinit var log: Log

    override fun processLog(log: Log) {
        this.log = log
        for (trace in log.traces)
            dependencyGraphProvider.processTrace(traceToNodeTrace(trace))
    }

    //TODO get rid of these
    internal val nodes: Set<Node>
        get() = dependencyGraphProvider.nodes
    internal val start: Node
        get() = dependencyGraphProvider.start
    internal val end: Node
        get() = dependencyGraphProvider.end

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
        for ((a, b) in dependencyGraphProvider.computeDependencyGraph())
            model.addDependency(a, b)
        val logWithNodes = log.traces
            .map { trace -> listOf(start) + traceToNodeTrace(trace) + listOf(end) }
        logWithNodes.forEach { trace ->
            longDistanceDependencyMiner.processTrace(trace)
        }
        mineBindings(logWithNodes, model)
        while (true) {
            val ltdeps = longDistanceDependencyMiner.mine(model)
            if (ltdeps.isNotEmpty()) {
                ltdeps.forEach { dep ->
                    model.addDependency(dep)
                    model.clearBindingsFor(dep.source)
                    model.clearBindingsFor(dep.target)
                }
                mineBindings(logWithNodes, model)
            } else
                break
        }

        if (logger().isTraceEnabled)
            logger().trace("Intermediate model:\n$model")

        val finalModel = MutableModel(start = start, end = end)
        finalModel.addInstance(*model.instances.filter { it.instanceId.isNullOrEmpty() }.toTypedArray())
        val dep2finalDep = HashMap<Dependency, Dependency>()
        for (dep in model.splits.values.flatten().flatMap { it.dependencies }.toSet()) {
            val s = Node(dep.source.activity, special = dep.source.special)
            val t = Node(dep.target.activity, special = dep.target.special)
            dep2finalDep[dep] = finalModel.addDependency(s, t)
        }
        for (split in model.splits.values.flatten()) {
            val s = Split(split.dependencies.map { dep2finalDep.getValue(it) }.toSet())
            if (!finalModel.contains(s))
                finalModel.addSplit(s)
        }
        for (join in model.joins.values.flatten()) {
            val j = Join(join.dependencies.map { dep2finalDep.getValue(it) }.toSet())
            if (!finalModel.contains(j))
                finalModel.addJoin(j)
        }

        if (logger().isTraceEnabled)
            logger().trace("Final model:\n$finalModel")

        finalModel
    }

}