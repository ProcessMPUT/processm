package processm.core.models.bpmn.converters

import processm.core.helpers.allSubsets
import processm.core.helpers.ifNullOrEmpty
import processm.core.models.bpmn.BPMNModel
import processm.core.models.bpmn.BPMNProcess
import processm.core.models.bpmn.jaxb.TCallActivity
import processm.core.models.bpmn.jaxb.TCollaboration
import processm.core.models.bpmn.jaxb.TProcess
import processm.core.models.causalnet.*

internal class BPMNModel2CausalNet(val model: BPMNModel) {

    private lateinit var combined: MutableCausalNet
    private lateinit var converters: Map<BPMNProcess, BPMN2CausalNet>

    private fun copy(causalNets: Collection<CausalNet>) {
        for (cnet in causalNets) {
            combined.copyFrom(cnet) { localToGlobal.getValue(cnet to it) }
        }
        combined.clearJoinsFor(combined.end)
        combined.clearSplitsFor(combined.start)
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
            val csrc = converters.getValue(src.first)
            val nsrc = csrc.nodes.getValue(src.second)
            val dst = model.byName(mf.targetRef)
            val cdst = converters.getValue(dst.first)
            val ndst = cdst.nodes.getValue(dst.second)
            Dependency(localToGlobal.getValue(csrc.cnet to nsrc.end), localToGlobal.getValue(cdst.cnet to ndst.start))
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

    private val localToGlobal = HashMap<Pair<CausalNet, Node>, Node>()

    internal fun prefix(p: TProcess): String {
        if (!p.name.isNullOrBlank())
            return "NAME:${p.name}"
        if (!p.id.isNullOrBlank())
            return "ID:${p.id}"
        val idx = model.processes.indexOfFirst { it.base === p }
        check(idx >= 0) { "Somehow we are processing an unknown TProcess" }
        return "IDX:${idx}"
    }

    fun toCausalNet(): MutableCausalNet {
        converters = model.processes.associateWith { BPMN2CausalNet(it) }
        val causalNets = converters.values.map { it.convert() }
        if (causalNets.size == 1)
            return causalNets.single()
        for (cnet in causalNets)
            localToGlobal.putAll(cnet.instances.associateBy { (cnet to it) })
        val node2converter = HashMap<Node, MutableList<BPMN2CausalNet>>()
        for (conv in converters.values)
            for (node in conv.cnet.instances) {
                node2converter.getOrPut(node, { ArrayList() }).add(conv)
            }
        for ((node, convs) in node2converter.filter { !it.key.special && it.value.size >= 2 }) {
            for (conv in convs) {
                val prefix = prefix(conv.bpmn.base)
                localToGlobal[conv.cnet to node] = Node(node.activity, prefix + (if (node.instanceId.isNotEmpty()) ":${node.instanceId}" else ""), node.special)
            }
        }
        combined = MutableCausalNet()
        copy(causalNets)
        terminalBindings()
        messageFlows()
        return combined
    }
}