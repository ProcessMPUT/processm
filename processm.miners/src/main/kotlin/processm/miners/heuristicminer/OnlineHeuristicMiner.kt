package processm.miners.heuristicminer

import processm.core.log.hierarchical.Log
import processm.core.log.hierarchical.Trace
import processm.core.logging.logger
import processm.core.models.causalnet.*
import processm.miners.heuristicminer.bindingproviders.BestFirstBindingProvider
import processm.miners.heuristicminer.bindingproviders.BindingProvider
import processm.miners.heuristicminer.dependencygraphproviders.BasicDependencyGraphProvider
import processm.miners.heuristicminer.dependencygraphproviders.DependencyGraphProvider
import processm.miners.heuristicminer.longdistance.LongDistanceDependencyMiner
import processm.miners.heuristicminer.longdistance.NaiveLongDistanceDependencyMiner
import processm.miners.heuristicminer.traceregisters.DifferentAdfixTraceRegister
import processm.miners.heuristicminer.traceregisters.TraceRegister

/**
 * An on-line implementation of the heuristic miner.
 * Given appropriate [traceRegister] and [longDistanceDependencyMiner] able to avoid storing all traces, but less efficient
 * than [OfflineHeuristicMiner].
 *
 * For the default values of parameters, the final model is guaranteed to have fitness = 1 for each of the presented traces.
 */
class OnlineHeuristicMiner(
    val minBindingSupport: Int = 1,
    val traceToNodeTrace: TraceToNodeTrace = BasicTraceToNodeTrace(),
    val dependencyGraphProvider: DependencyGraphProvider = BasicDependencyGraphProvider(1),
    val longDistanceDependencyMiner: LongDistanceDependencyMiner = NaiveLongDistanceDependencyMiner(),
    val bindingProvider: BindingProvider = BestFirstBindingProvider(),
    val traceRegister: TraceRegister = DifferentAdfixTraceRegister()
) : HeuristicMiner {

    companion object {
        private val logger = logger()
    }

    private var unableToReplay = ArrayList<List<Node>>()
    private var currentBindings = setOf<Binding>()
    private val model = MutableCausalNet(start = dependencyGraphProvider.start, end = dependencyGraphProvider.end)
    override val result: MutableCausalNet = model

    override fun processLog(log: Log) {
        for (trace in log.traces)
            processTrace(trace)
    }

    fun processTrace(trace: Trace) {
        val nodeTrace = traceToNodeTrace(trace)
        dependencyGraphProvider.processTrace(nodeTrace)

        model.addInstance(*(nodeTrace.toSet() - model.instances).toTypedArray())
        model.clearDependencies()
        for (dep in dependencyGraphProvider.computeDependencyGraph())
            model.addDependency(dep)

        val nodeTraceWithLimits = listOf(model.start) + nodeTrace + listOf(model.end)
        mineBindings(nodeTraceWithLimits)
        longDistanceDependencyMiner.processTrace(nodeTraceWithLimits)
        while (true) {
            val ltdeps = longDistanceDependencyMiner.mine(model)
            if (ltdeps.isNotEmpty()) {
                ltdeps.forEach { dep ->
                    model.addDependency(dep)
                    model.clearBindingsFor(dep.source)
                    model.clearBindingsFor(dep.target)
                }
                val affectedNodes = (ltdeps.map { it.source } + ltdeps.map { it.target }).toSet()
                val affectedTraces = currentBindings
                    .filter { binding ->
                        binding.dependencies.any { affectedNodes.contains(it.source) || affectedNodes.contains(it.target) }
                    }
                    .flatMap { traceRegister[it] }
                    .toSet()
                replay(affectedTraces)
                updateBindings()
            } else
                break
        }
    }

    private fun replay(toReplay: Collection<List<Node>>) {
        traceRegister.removeAll(toReplay)
        unableToReplay.addAll(toReplay)
        val i = unableToReplay.iterator()
        while (i.hasNext()) {
            val trace = i.next()
            val bindings = bindingProvider.computeBindings(model, trace)
            if (bindings.isNotEmpty()) {
                logger.trace("replaying succeeded: $trace")
                traceRegister.register(bindings, trace)
                i.remove()
            } else
                logger.trace("replaying failed: $trace")
        }
    }

    private fun bestBindings(): Set<Binding> {
        return traceRegister.selectBest { it.size >= minBindingSupport }
    }

    private fun updateBindings() {
        model.clearBindings()
        currentBindings = bestBindings()
        for (binding in currentBindings)
            if (binding is Split)
                model.addSplit(binding)
            else
                model.addJoin(binding as Join)
    }

    private fun mineBindings(nodeTrace: List<Node>) {
        model.clearBindings()
        val bindings = bindingProvider.computeBindings(model, nodeTrace)
        if (bindings.isNotEmpty()) {
            traceRegister.register(bindings, nodeTrace)
            val bestBindings = bestBindings()
            val availableDependencies = (model.outgoing.values.flatten() + model.incoming.values.flatten()).toSet()

            val bindingsWithUnavailableDependencies =
                bestBindings.filter { !availableDependencies.containsAll(it.dependencies) }.toSet()

            val (old, new) = bestBindings.partition { currentBindings.contains(it) }
            val dependenciesOfNewBindings = new.flatMap { it.dependencies }.toSet()
            val bindingsWithDependenciesOfNewBindings = old
                .filter { binding ->
                    binding.dependencies.any { dependenciesOfNewBindings.contains(it) }
                }

            val toReplay = (bindingsWithUnavailableDependencies + bindingsWithDependenciesOfNewBindings)
                .flatMap { traceRegister[it] }
                .toSet()

            replay(toReplay)
            updateBindings()
        } else {
            logger.trace("computing bindings failed, keeping for later: $nodeTrace")
            unableToReplay.add(nodeTrace)
        }
    }
}