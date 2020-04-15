package processm.core.models.bpmn.converters

import processm.core.helpers.Counter
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
    private val names = Counter<String>()

    private fun createName(bpmnActivity: BPMNFlowNode): String {
        var name = bpmnActivity.name
        val n = names[name]
        names.inc(name)
        if (n > 0)
            name += ":$n"
        return name
    }

    private fun fillNodes() {
        val nodes = HashMap<BPMNFlowNode, SplitNode>()
        for (bpmnActivity in bpmn.allActivities) {
            val name = createName(bpmnActivity)
            if (bpmnActivity.base is TSubProcess) {
                val startNode = Node(name, "start")
                val endNode = Node(name, "end")
                nodes[bpmnActivity] = SplitNode(startNode, endNode)
                cnet.addInstance(startNode, endNode)
            } else {
                val node = Node(name)
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

    private val boundaryEventDependencies = HashSet<Dependency>()

    private fun fillDependencies() {
        boundaryEventDependencies.clear()
        for (seqflow in bpmn.recursiveFlowElements.filterIsInstance<TSequenceFlow>()) {
            val src = nodes.getValue(bpmn.get(seqflow.sourceRef as TFlowNode))
            val dst = nodes.getValue(bpmn.get(seqflow.targetRef as TFlowNode))
            cnet.addDependency(src.end, dst.start)
        }
        for (event in bpmn.recursiveFlowElements.filterIsInstance<TBoundaryEvent>()) {
            val src = nodes.getValue(bpmn.get(bpmn.flowByName(event.attachedToRef)))
            val dst = nodes.getValue(bpmn.get(event))
            val dep = cnet.addDependency(src.end, dst.start)
            boundaryEventDependencies.add(dep)
        }
    }

    private fun fillTerminalDependenciesForSubprocesses() {
        for ((bpmnActivity, snode) in nodes) {
            val base = bpmnActivity.base
            if (base is TSubProcess) {
                val children = base.flowElement.map { it.value }.filterIsInstance<TFlowNode>().map { nodes.getValue(bpmn.get(it)) }
                if (children.isNotEmpty()) { // Distinguish between expanded and collapsed subprocess
                    println(cnet)
                    val startChildren = children.filter { cnet.incoming[it.start].isNullOrEmpty() }
                    check(startChildren.isNotEmpty()) { "Subprocess ${base.name} with seemingly no start elements" }
                    val endChildren = children.filter { cnet.outgoing[it.end].isNullOrEmpty() }
                    check(endChildren.isNotEmpty()) { "Subprocess with seemingly no end elements" }
                    for (child in startChildren)
                        cnet.addDependency(snode.start, child.start)
                    val endDeps = endChildren.map { child -> Dependency(child.end, snode.end) }
                    cnet.addDependencies(endDeps)
                    //TODO check semantics
                    for (subset in endDeps.allSubsets().filter { it.isNotEmpty() })
                        cnet.addJoin(Join(subset.toSet()))
                } else {
                    val dep = cnet.addDependency(snode.start, snode.end)
                    cnet.addSplit(Split(setOf(dep)))
                    cnet.addJoin(Join(setOf(dep)))
                }
            }
        }
    }

    private fun processGateways() {
        for ((gateway, gnode) in nodes) {
            if (gateway is BPMNGateway) {
                val base = gateway.base
                if (base is TExclusiveGateway || (base is TEventBasedGateway && base.eventGatewayType == TEventBasedGatewayType.EXCLUSIVE)) {
                    for (src in gateway.incoming)
                        cnet.addJoin(Join(setOf(Dependency(nodes.getValue(src).end, gnode.start))))
                    for (dst in gateway.outgoing)
                        cnet.addSplit(Split(setOf(Dependency(gnode.end, nodes.getValue(dst).start))))
                } else if (gateway.base is TParallelGateway || (base is TEventBasedGateway && base.eventGatewayType == TEventBasedGatewayType.PARALLEL)) {
                    cnet.addJoin(Join(gateway.incoming.map { src -> Dependency(nodes.getValue(src).end, gnode.start) }.toSet()))
                    cnet.addSplit(Split(gateway.outgoing.map { dst -> Dependency(gnode.end, nodes.getValue(dst).start) }.toSet()))
                } else
                    TODO("You lazy bum, implement ${gateway.base::class}")
            }
        }
        for (dep in boundaryEventDependencies)
            cnet.addSplit(Split(setOf(dep)))
        for (other in nodes.filter { it.key !is BPMNGateway }) {
            val tmp = cnet.outgoing.getValue(other.value.end) - boundaryEventDependencies
            val s = Split(tmp)
            if (s !in cnet)
                cnet.addSplit(s)
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
        if (bpmn.recursiveFlowElements.any { it is TActivity && it.isIsForCompensation })
            throw UnsupportedOperationException("Compensations are not supported")
        if (bpmn.recursiveFlowElements.any { it is TCallActivity })
            throw UnsupportedOperationException("Callable elements are not supported")
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