package processm.miners.heuristicminer

import processm.core.log.hierarchical.Log
import processm.core.log.hierarchical.Trace
import processm.core.models.causalnet.*
import processm.miners.heuristicminer.hypothesisselector.MostGreedyHypothesisSelector
import processm.miners.heuristicminer.hypothesisselector.ReplayTraceHypothesisSelector
import processm.miners.heuristicminer.longdistance.LongDistanceDependencyMiner
import processm.miners.heuristicminer.longdistance.NaiveLongDistanceDependencyMiner
import processm.miners.heuristicminer.traceregisters.DifferentAdfixTraceRegister
import processm.miners.heuristicminer.traceregisters.TraceRegister


class OnlineHeuristicMiner(
    minDirectlyFollows: Int = 1,
    minDependency: Double = 1e-10,
    val minBindingSupport: Int = 1,
    val longDistanceDependencyMiner: LongDistanceDependencyMiner = NaiveLongDistanceDependencyMiner(),
    hypothesisSelector: ReplayTraceHypothesisSelector = MostGreedyHypothesisSelector(),
    val traceRegister: TraceRegister = DifferentAdfixTraceRegister()
) : AbstractHeuristicMiner(minDirectlyFollows, minDependency, hypothesisSelector) {

    override fun processLog(log: Log) {
        for (trace in log.traces)
            processTrace(trace)
    }

    fun processTrace(trace: Trace) {
        val nodeTrace = traceToNodeTrace(trace)
        updateDirectlyFollows(nodeTrace)

        model.addInstance(*(nodeTrace.toSet() - model.instances).toTypedArray())
        //TODO consider only pairs (in both directions!) from the trace and add/remove accordingly
        model.clearDependencies()
        for ((a, b) in computeDependencyGraph())
            model.addDependency(a, b)

        val nodeTraceWithLimits = listOf(start) + nodeTrace + listOf(end)
        mineBindings(nodeTraceWithLimits)
        longDistanceDependencyMiner.processTrace(nodeTraceWithLimits)
        while (true) {
            val ltdeps = longDistanceDependencyMiner.mine(model)
            if (ltdeps.isNotEmpty()) {
                ltdeps.forEach { dep ->
                    model.addDependency(dep.first, dep.second)
                    model.clearBindingsFor(dep.first)
                    model.clearBindingsFor(dep.second)
                }
                val affectedNodes = (ltdeps.map { it.first } + ltdeps.map { it.second }).toSet()
                val affectedTraces = oldBindings
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
        println(model)
    }


    private var unableToReplay = ArrayList<List<Node>>()
    private var oldBindings = setOf<Binding>()

    private fun replay(toReplay: Collection<List<Node>>) {
        traceRegister.removeAll(toReplay)
        unableToReplay.addAll(toReplay)
        val i = unableToReplay.iterator()
        while (i.hasNext()) {
            val trace = i.next()
            val bindings = computeBindings(model, trace)
            if (bindings.isNotEmpty()) {
                println("REPLAYING $trace OK")
                traceRegister.register(bindings, trace)
                i.remove()
            } else
                println("REPLAYING $trace FAILED")
        }
    }

    private fun updateBindings() {
        model.clearBindings()
        val bestBindings = traceRegister.selectBest { it.size >= minBindingSupport }
        oldBindings = bestBindings
        for (binding in bestBindings)
            if (binding is Split)
                model.addSplit(binding)
            else
                model.addJoin(binding as Join)
    }

    private fun mineBindings(nodeTrace: List<Node>) {
        model.clearBindings()
        val bindings = computeBindings(model, nodeTrace)
        if (bindings.isNotEmpty()) {
            traceRegister.register(bindings, nodeTrace)
            var bestBindings = traceRegister.selectBest { it.size >= minBindingSupport }
            val availableDependencies = (model.outgoing.values.flatten() + model.incoming.values.flatten()).toSet()

            val bindingsWithUnavailableDependencies =
                bestBindings.filter { !availableDependencies.containsAll(it.dependencies) }.toSet()

            val (old, new) = bestBindings.partition { oldBindings.contains(it) }
            val dependenciesOfNewBindings = new.flatMap { it.dependencies }.toSet()
            val bindingsWithDependenciesOfNewBindings = old
                .filter { binding ->
                    binding.dependencies.any { dependenciesOfNewBindings.contains(it) }
                }
            println("OLD $old")
            println("NEW $new")
            println("BWDONB $bindingsWithDependenciesOfNewBindings")
            println("BWUD $bindingsWithUnavailableDependencies")

            val toReplay = (bindingsWithUnavailableDependencies + bindingsWithDependenciesOfNewBindings)
                .flatMap { traceRegister[it] }
                .toSet()

            if (unableToReplay.isNotEmpty() || toReplay.isNotEmpty()) {
                replay(toReplay)
            }
            updateBindings()
        } else {
            println("FAILED $nodeTrace")
            unableToReplay.add(nodeTrace)
        }
    }

    private val model = MutableModel(start = start, end = end)

    override val result: MutableModel = model


}