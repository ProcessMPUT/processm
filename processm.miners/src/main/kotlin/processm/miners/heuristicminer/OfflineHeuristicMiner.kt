package processm.miners.heuristicminer

import processm.core.helpers.mapToSet
import processm.core.log.hierarchical.Log
import processm.core.logging.logger
import processm.core.logging.trace
import processm.core.models.causalnet.*
import processm.core.verifiers.causalnet.CausalNetVerifierImpl
import processm.miners.heuristicminer.bindingproviders.BestFirstBindingProvider
import processm.miners.heuristicminer.bindingproviders.BindingProvider
import processm.miners.heuristicminer.bindingselectors.BindingSelector
import processm.miners.heuristicminer.bindingselectors.CountSeparately
import processm.miners.heuristicminer.dependencygraphproviders.BasicDependencyGraphProvider
import processm.miners.heuristicminer.dependencygraphproviders.DependencyGraphProvider
import processm.miners.heuristicminer.longdistance.LongDistanceDependencyMiner
import processm.miners.heuristicminer.longdistance.NaiveLongDistanceDependencyMiner

/**
 * An off-line implementation of Heuristic Miner.
 *
 * It is more efficient than [OnlineHeuristicMiner], as it does not need to recompute the model after each trace.
 * For the default values of parameters, the final model is guaranteed to have fitness = 1 for each of the presented traces.
 */
class OfflineHeuristicMiner(
    val traceToNodeTrace: TraceToNodeTrace = BasicTraceToNodeTrace(),
    val dependencyGraphProvider: DependencyGraphProvider = BasicDependencyGraphProvider(1),
    val longDistanceDependencyMiner: LongDistanceDependencyMiner = NaiveLongDistanceDependencyMiner(),
    val splitSelector: BindingSelector<Split> = CountSeparately(1),
    val joinSelector: BindingSelector<Join> = CountSeparately(1),
    val bindingProvider: BindingProvider = BestFirstBindingProvider()
) : HeuristicMiner {
    private lateinit var log: Sequence<NodeTrace>

    override fun processLog(log: Log) {
        this.log = log.traces.map { traceToNodeTrace(it) }
        for (trace in this.log)
            dependencyGraphProvider.processTrace(trace)
    }

    private fun mineBindings(
        logWithNodes: Sequence<List<Node>>,
        model: MutableModel
    ) {
        joinSelector.reset()
        splitSelector.reset()
        for (trace in logWithNodes) {
            val (joins, splits) = bindingProvider.computeBindings(model, trace).partition { it is Join }
            joinSelector.add(joins.map { it as Join })
            splitSelector.add(splits.map { it as Split })
        }
        val joins = joinSelector.best
        val splits = splitSelector.best
        check(joins.isNotEmpty() && splits.isNotEmpty()) { "Failed to compute bindings. Aborting, as the final model will not be sound anyhow." }
        for (join in joins)
            if (!model.contains(join))
                model.addJoin(join)
        for (split in splits)
            if (!model.contains(split))
                model.addSplit(split)
    }

    private fun removeUnusedParts(model: MutableModel): MutableModel {
        val usedDependencies = model.splits.values.flatten().flatMap { it.dependencies }.toSet()
        val usedNodes = (usedDependencies.map { it.source } + usedDependencies.map { it.target }).toSet()
        //If start or end are not used something went horribly wrong and we may as well throw, as the final model won't even have a connected dependency graph
        check(usedNodes.contains(model.start))
        check(usedNodes.contains(model.end))
        val finalModel = MutableModel(start = model.start, end = model.end)
        finalModel.addInstance(*usedNodes.toTypedArray())
        val dep2finalDep = HashMap<Dependency, Dependency>()
        for (dep in usedDependencies) {
            val s = Node(dep.source.activity, special = dep.source.special)
            val t = Node(dep.target.activity, special = dep.target.special)
            dep2finalDep[dep] = finalModel.addDependency(s, t)
        }
        for (split in model.splits.values.flatten()) {
            val s = Split(split.dependencies.mapToSet { dep2finalDep.getValue(it) })
            if (!finalModel.contains(s))
                finalModel.addSplit(s)
        }
        for (join in model.joins.values.flatten()) {
            val j = Join(join.dependencies.mapToSet { dep2finalDep.getValue(it) })
            if (!finalModel.contains(j))
                finalModel.addJoin(j)
        }

        logger().trace { "Final model:\n$finalModel" }

        return finalModel
    }

    override val result: MutableModel by lazy {
        val model = MutableModel(start = dependencyGraphProvider.start, end = dependencyGraphProvider.end)
        model.addInstance(*dependencyGraphProvider.nodes.toTypedArray())
        for (dep in dependencyGraphProvider.computeDependencyGraph())
            model.addDependency(dep)

        logger().trace { "Dependency graph (connected=${CausalNetVerifierImpl(model).isConnected}):\n$model" }

        val logWithNodes = log
            .map { trace -> listOf(dependencyGraphProvider.start) + trace + listOf(dependencyGraphProvider.end) }
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

        logger().trace { "Intermediate model:\n$model" }

        return@lazy removeUnusedParts(model)
    }

}