package processm.core.models.bpmn

import processm.core.helpers.allSubsets
import processm.core.models.bpmn.jaxb.TEndEvent
import processm.core.models.bpmn.jaxb.TExclusiveGateway
import processm.core.models.bpmn.jaxb.TFlowNode
import processm.core.models.bpmn.jaxb.TSequenceFlow
import processm.core.models.causalnet.*


private fun MutableModel.addDependencies(deps: Iterable<Dependency>) {
    for (dep in deps)
        this.addDependency(dep)
}

internal class BPMN2CausalNet(val bpmn: BPMNProcess) {
    private lateinit var cnet: MutableModel
    private lateinit var nodes: Map<BPMNFlowNode, Node>

    private fun fillNodes() {
        nodes = bpmn.allActivities.associateWith { Node(it.name) }
        println(nodes)
        cnet.addInstance(*nodes.values.toTypedArray())
    }

    private fun fillStart() {
        val startDeps = bpmn.startActivities.map { Dependency(cnet.start, nodes.getValue(it)) }.toSet()
        cnet.addDependencies(startDeps)
        cnet.addSplit(Split(startDeps))   // TODO check semantics with the spec
    }

    private fun fillEnd() {
        // BPMN spec: All the tokens that were generated within the Process MUST be consumed by an End Event before the Process has been completed.
        // I think this calls for the powerset of incoming dependencies - this possibly will generate some dead joins, but otherwise we need to know here what are the possible paths
        val endDeps = bpmn.endActivities.map { Dependency(nodes.getValue(it), cnet.end) }.toSet()
        cnet.addDependencies(endDeps)
        for (subset in endDeps.allSubsets().filter { it.isNotEmpty() })
            cnet.addJoin(Join(subset.toSet()))
        for(endevent in bpmn.endActivities.filterIsInstance<BPMNEvent>().filter { it.base is TEndEvent }) {
            val endeventnode=nodes.getValue(endevent)
            for(subset in endevent.incoming.map { Dependency(nodes.getValue(it), endeventnode) } .allSubsets().filter { it.isNotEmpty() })
                cnet.addJoin(Join(subset.toSet()))
        }
    }

    private fun fillDependencies() {
        for ((bpmnsrc, src) in nodes.entries) {
            for (dstname in bpmnsrc.base.outgoing) {
                println("dstname=$dstname")
                val dst = nodes.getValue(bpmn.get(bpmn.flowByName<TSequenceFlow>(dstname).targetRef as TFlowNode))
                cnet.addDependency(src, dst)
            }
        }
    }

    private fun processGateways() {

        //TODO process gateways here
        for ((gateway, gnode) in nodes) {
            if (gateway is BPMNGateway) {
                if (gateway.base is TExclusiveGateway) {
                    if (gateway.isJoin) {
                        for (src in gateway.incoming)
                            cnet.addJoin(Join(setOf(Dependency(nodes.getValue(src), gnode))))
                    }
                    if (gateway.isSplit) {
                        for (dst in gateway.outgoing)
                            cnet.addSplit(Split(setOf(Dependency(gnode, nodes.getValue(dst)))))
                    }
                } else TODO()
            }
        }
        for ((node, deps) in cnet.outgoing)
            if (deps.size == 1) {
                val s = Split(deps)
                if (s !in cnet)
                    cnet.addSplit(s)
            }
        for ((node, deps) in cnet.incoming)
            if (deps.size == 1) {
                val j = Join(deps)
                if (j !in cnet)
                    cnet.addJoin(j)
            }
    }

    fun convert(): MutableModel {
        cnet = MutableModel()
        fillNodes()
        fillDependencies()
        fillStart()
        fillEnd()
        processGateways()
        return cnet
    }
}