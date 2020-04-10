package processm.core.models.bpmn.converters

import processm.core.helpers.allSubsets
import processm.core.models.bpmn.BPMNEvent
import processm.core.models.bpmn.BPMNFlowNode
import processm.core.models.bpmn.BPMNGateway
import processm.core.models.bpmn.BPMNProcess
import processm.core.models.bpmn.jaxb.*
import processm.core.models.causalnet.*
import processm.core.models.causalnet.Node


private fun MutableCausalNet.addDependencies(deps: Iterable<Dependency>) {
    for (dep in deps)
        this.addDependency(dep)
}

internal class BPMN2CausalNet(val bpmn: BPMNProcess) {

    internal data class SplitNode(val start: Node, val end: Node)

    internal lateinit var cnet: MutableCausalNet
    internal lateinit var nodes: Map<BPMNFlowNode, SplitNode>

    private fun fillNodes() {
        val nodes = HashMap<BPMNFlowNode, SplitNode>()
        for (bpmnActivity in bpmn.allActivities) {
            if (bpmnActivity.base is TSubProcess) {
                val startNode = Node(bpmnActivity.name, "start")
                val endNode = Node(bpmnActivity.name, "end")
                nodes[bpmnActivity] = SplitNode(startNode, endNode)
                cnet.addInstance(startNode, endNode)
            } else {
                val node = Node(bpmnActivity.name)
                nodes[bpmnActivity] = SplitNode(node, node)
                cnet.addInstance(node)
            }
        }
        this.nodes = nodes
    }

    private fun fillStart() {
        val startDeps = bpmn.startActivities.map { Dependency(cnet.start, nodes.getValue(it).start) }.toSet()
        cnet.addDependencies(startDeps)
        cnet.addSplit(Split(startDeps))   // TODO check semantics with the spec
    }

    private fun fillEnd() {
        // BPMN spec: All the tokens that were generated within the Process MUST be consumed by an End Event before the Process has been completed.
        // I think this calls for the powerset of incoming dependencies - this possibly will generate some dead joins, but otherwise we need to know here what are the possible paths
        val endDeps = bpmn.endActivities.map { Dependency(nodes.getValue(it).end, cnet.end) }.toSet()
        cnet.addDependencies(endDeps)
        for (subset in endDeps.allSubsets().filter { it.isNotEmpty() })
            cnet.addJoin(Join(subset.toSet()))
        for (endevent in bpmn.endActivities.filterIsInstance<BPMNEvent>().filter { it.base is TEndEvent }) {
            val endeventnode = nodes.getValue(endevent)
            for (subset in endevent.incoming.map { Dependency(nodes.getValue(it).end, endeventnode.start) }.allSubsets().filter { it.isNotEmpty() })
                cnet.addJoin(Join(subset.toSet()))
        }
    }

    private fun fillDependencies() {
        for ((bpmnsrc, src) in nodes.entries) {
            for (dstname in bpmnsrc.base.outgoing) {
                println("dstname=$dstname")
                val dst = nodes.getValue(bpmn.get(bpmn.flowByName<TSequenceFlow>(dstname).targetRef as TFlowNode))
                cnet.addDependency(src.end, dst.start)
            }
        }
    }

    private fun fillTerminalDependenciesForSubprocesses() {
        for ((bpmnActivity, snode) in nodes) {
            val base = bpmnActivity.base
            if (base is TSubProcess) {
                val children = base.flowElement.map { it.value }.filterIsInstance<TFlowNode>().map { nodes.getValue(bpmn.get(it)) }
                val startChildren = children.filter { cnet.incoming[it.start].isNullOrEmpty() }
                check(startChildren.isNotEmpty()) { "Subprocess with seemingly no start elements" }
                val endChildren = children.filter { cnet.outgoing[it.end].isNullOrEmpty() }
                check(endChildren.isNotEmpty()) { "Subprocess with seemingly no end elements" }
                for (child in startChildren)
                    cnet.addDependency(snode.start, child.start)
                for (child in endChildren)
                    cnet.addDependency(child.end, snode.end)
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
                            cnet.addJoin(Join(setOf(Dependency(nodes.getValue(src).end, gnode.start))))
                    }
                    if (gateway.isSplit) {
                        for (dst in gateway.outgoing)
                            cnet.addSplit(Split(setOf(Dependency(gnode.end, nodes.getValue(dst).start))))
                    }
                } else TODO()
            }
        }
        for (other in nodes.filter { it.key !is BPMNGateway }) {
            if (other.key.isSplit) {   // this is not a gateway, but it is a split -> assuming parallel gateway (BPMN spec. ch. 13.2.2)
                val s = Split(cnet.outgoing.getValue(other.value.end))
                if (s !in cnet)
                    cnet.addSplit(s)
            }
            if (other.key.isJoin)    // this is not a gateway, but it is a join -> assuming exclusive gateway (BPMN spec. ch. 13.2.2)
                for (dep in cnet.incoming.getValue(other.value.start)) {
                    val j = Join(setOf(dep))
                    if (j !in cnet)
                        cnet.addJoin(j)
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

    fun convert(): MutableCausalNet {
        cnet = MutableCausalNet()
        fillNodes()
        fillDependencies()
        fillTerminalDependenciesForSubprocesses()
        fillStart()
        fillEnd()
        processGateways()
        return cnet
    }
}