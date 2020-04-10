package processm.core.models.bpmn.converters

import processm.core.helpers.allSubsets
import processm.core.helpers.ifNullOrEmpty
import processm.core.models.bpmn.BPMNModel
import processm.core.models.bpmn.BPMNProcess
import processm.core.models.bpmn.jaxb.TCollaboration
import processm.core.models.causalnet.*

internal class BPMNModel2CausalNet(val model: BPMNModel) {

    private lateinit var combined: MutableCausalNet
    private lateinit var converters: Map<BPMNProcess, BPMN2CausalNet>

    private fun copy(causalNets: Collection<CausalNet>) {
        for (cnet in causalNets) {
            for (node in cnet.instances)
                combined.addInstance(node)
            for (dependency in cnet.outgoing.values.flatten())
                combined.addDependency(dependency)
            for (split in cnet.splits.values.flatten())
                if (split.source != cnet.start)
                    combined.addSplit(split)
            for (join in cnet.joins.values.flatten())
                if (join.target != cnet.end)
                    combined.addJoin(join)
        }
    }

    private fun terminalBindings() {
        combined.addSplit(Split(combined.outgoing.getValue(combined.start)))
        for (ends in combined.incoming.getValue(combined.end).allSubsets().filter { it.isNotEmpty() })
            combined.addJoin(Join(ends.toSet()))
    }

    private fun messageFlows() {
        val messageflows = model.model
                .rootElement
                .map { it.value }
                .filterIsInstance<TCollaboration>()
                .flatMap { it.messageFlow }
        val newDependencies = messageflows.map { mf ->
            val src = model.byName(mf.sourceRef)
            val nsrc = converters.getValue(src.first).nodes.getValue(src.second)
            val dst = model.byName(mf.targetRef)
            val ndst = converters.getValue(dst.first).nodes.getValue(dst.second)
            Dependency(nsrc.end, ndst.start)
        }
        for (dep in newDependencies)
            combined.addDependency(dep)
        for ((src, deps) in newDependencies.groupBy { it.source }) {
            val newSplits = combined
                    .splits[src]
                    ?.map { Split(it.dependencies + deps) }
                    .ifNullOrEmpty { listOf(Split(deps.toSet())) }
            combined.clearSplitsFor(src)
            for (split in newSplits)
                combined.addSplit(split)
        }
        for ((dst, deps) in newDependencies.groupBy { it.target }) {
            val newJoins = combined
                    .joins[dst]
                    ?.map { Join(it.dependencies + deps) }
                    .ifNullOrEmpty { listOf(Join(deps.toSet())) }
            combined.clearJoinsFor(dst)
            for (join in newJoins)
                combined.addJoin(join)
        }
    }

    fun toCausalNet(): MutableCausalNet {
        converters = model.processes.associateWith { BPMN2CausalNet(it) }
        val causalNets = converters.values.map { it.convert() }
        if (causalNets.size == 1)
            return causalNets.single()
        check(causalNets
                .mapIndexed { leftidx, left ->
                    causalNets
                            .filterIndexed { rightidx, right -> leftidx < rightidx }
                            .map { right -> left.instances.filter { !it.special }.intersect(right.instances) }
                }
                .flatten().all { it.isEmpty() }) { "Two processes within the same model share the same instance. Possibly perform some renaming or what?" }
        combined = MutableCausalNet()
        copy(causalNets)
        terminalBindings()
        messageFlows()
        return combined
    }
}