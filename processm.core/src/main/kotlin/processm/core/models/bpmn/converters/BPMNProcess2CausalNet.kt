package processm.core.models.bpmn.converters

import processm.core.helpers.Counter
import processm.core.helpers.allSubsets
import processm.core.models.bpmn.BPMNActivity
import processm.core.models.bpmn.BPMNFlowNode
import processm.core.models.bpmn.BPMNProcess
import processm.core.models.causalnet.*


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
            if (bpmnActivity.isComposite) {
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

    private fun startBindings(startDeps: Collection<Dependency>) {
        // TODO check semantics with the spec
        cnet.addSplit(Split(startDeps.toSet()))
        for (dep in startDeps)
            cnet.addJoin(Join(setOf(dep)))
    }

    private fun fillStart() {
        val startDeps = bpmn.startActivities.map { Dependency(cnet.start, nodes.getValue(it).start) }.toSet()
        cnet.addDependencies(startDeps)
        startBindings(startDeps)
    }

    private fun endBindings(endDeps: Collection<Dependency>) {
        for (dep in endDeps)
            cnet.addSplit(Split(setOf(dep)))
        for (subset in endDeps.allSubsets().filter { it.isNotEmpty() })
            cnet.addJoin(Join(subset.toSet()))
    }

    private fun fillEnd() {
        // BPMN spec: All the tokens that were generated within the Process MUST be consumed by an End Event before the Process has been completed.
        // I think this calls for the powerset of incoming dependencies - this possibly will generate some dead joins, but otherwise we need to know here what are the possible paths
        val endDeps = bpmn.endActivities.map { Dependency(nodes.getValue(it).end, cnet.end) }.toSet()
        cnet.addDependencies(endDeps)
        endBindings(endDeps)
    }

    private fun fillDependencies() {
        for ((gateway, gnode) in nodes) {
            for (split in gateway.split.possibleOutcomes) {
                val deps = split.activities.map { Dependency(gnode.end, nodes.getValue(it).start) }.toSet()
                cnet.addDependencies(deps)
                cnet.addSplit(Split(deps))
            }
            for (join in gateway.join.possibleOutcomes) {
                val deps = join.activities.map { Dependency(nodes.getValue(it).end, gnode.start) }.toSet()
                cnet.addDependencies(deps)
                cnet.addJoin(Join(deps))
            }
        }
    }

    private fun fillTerminalDependenciesForSubprocesses() {
        for ((bpmnActivity, snode) in nodes.filterKeys { it.isComposite }) {
            val children = bpmnActivity.children.map { nodes.getValue(it) }
            if (children.isNotEmpty()) { // Distinguish between expanded and collapsed subprocess
                val startChildren = children.filter { cnet.incoming[it.start].isNullOrEmpty() }
                check(startChildren.isNotEmpty()) { "Subprocess with seemingly no start elements" }
                val endChildren = children.filter { cnet.outgoing[it.end].isNullOrEmpty() }
                check(endChildren.isNotEmpty()) { "Subprocess with seemingly no end elements" }
                val startDeps = startChildren.map { child -> Dependency(snode.start, child.start) }
                cnet.addDependencies(startDeps)
                startBindings(startDeps)
                val endDeps = endChildren.map { child -> Dependency(child.end, snode.end) }
                cnet.addDependencies(endDeps)
                endBindings(endDeps)
            } else {
                val dep = cnet.addDependency(snode.start, snode.end)
                cnet.addSplit(Split(setOf(dep)))
                cnet.addJoin(Join(setOf(dep)))
            }
        }
    }

    fun convert(): MutableCausalNet {
        if (bpmn.allActivities.filterIsInstance<BPMNActivity>().any { it.isForCompensation })
            throw UnsupportedOperationException("Compensations are not supported")
        if (bpmn.allActivities.filterIsInstance<BPMNActivity>().any { it.isCallable })
            throw UnsupportedOperationException("Callable elements are not supported")
        cnet = MutableCausalNet()
        fillNodes()
        fillDependencies()
        fillTerminalDependenciesForSubprocesses()
        fillStart()
        fillEnd()
        return cnet
    }
}