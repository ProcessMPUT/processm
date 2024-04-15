package processm.core.models.bpmn.converters

import processm.core.models.bpmn.BPMNModel
import processm.core.models.bpmn.jaxb.*
import processm.core.models.processtree.*
import java.util.*
import processm.core.models.processtree.Node as PTNode

private class ProcessTree2BPMN(private val tree: ProcessTree) : ToBPMN() {

    private val activities = IdentityHashMap<ProcessTreeActivity, TTask>()

    private fun convert(tnode: PTNode): Pair<TFlowNode, TFlowNode> =
        when (tnode) {
            is Sequence -> convertSequence(tnode)
            is Exclusive -> convertExclusive(tnode)
            is Parallel -> convertParallel(tnode)
            is RedoLoop -> convertRedoLoop(tnode)
            is ProcessTreeActivity -> convertActivity(tnode)
            else -> throw IllegalArgumentException("Unknown node type: ${tnode.javaClass.name}")
        }

    private fun convertRedoLoop(tnode: RedoLoop): Pair<TFlowNode, TFlowNode> {
        require(tnode.children.size >= 2)
        val (firstStart, firstEnd) = convert(tnode.children.first())
        val (restStart, restEnd) = convertToGateway(tnode.children.subList(1, tnode.children.size)) {
            add(TExclusiveGateway())
        }
        link(firstEnd, restStart)
        link(restEnd, firstStart)
        return firstStart to restEnd
    }

    private inline fun <reified T : TGateway> convertToGateway(
        children: Collection<PTNode>,
        factory: () -> T
    ): Pair<TFlowNode, TFlowNode> {
        if (children.size == 1) return convert(children.single())
        require(children.size > 1)
        val diverging = factory().apply {
            gatewayDirection = TGatewayDirection.DIVERGING
        }
        val converging = factory().apply {
            gatewayDirection = TGatewayDirection.CONVERGING
        }
        for (child in children) {
            val (s, e) = convert(child)
            link(diverging, s)
            link(e, converging)
        }
        return diverging to converging
    }

    private fun convertParallel(tnode: Parallel): Pair<TFlowNode, TFlowNode> =
        convertToGateway(tnode.children) { add(TParallelGateway()) }

    private fun convertExclusive(tnode: Exclusive): Pair<TFlowNode, TFlowNode> =
        convertToGateway(tnode.children) { add(TExclusiveGateway()) }


    private fun convertActivity(activity: ProcessTreeActivity): Pair<TTask, TTask> {
        val task = activities.computeIfAbsent(activity) {
            return@computeIfAbsent add(TTask().apply { name = activity.name })
        }
        return task to task
    }

    private fun convertSequence(sequence: Sequence): Pair<TFlowNode, TFlowNode> {
        val i = sequence.children.iterator()
        if (i.hasNext()) {
            var (start, end) = convert(i.next())
            while (i.hasNext()) {
                val (s, e) = convert(i.next())
                link(end, s)
                end = e
            }
            return start to end
        }
        error("A sequence with no children")
    }

    private fun pruneSilents() {
        for (activity in tree.activities) {
            if (!activity.isSilent) continue
            val task = activities[activity] ?: continue
            val incoming = flowElements.filter { (it.value as? TSequenceFlow)?.targetRef == task }
            val outgoing = flowElements.filter { (it.value as? TSequenceFlow)?.sourceRef == task }
            if (incoming.size == 1 && outgoing.size == 1) {
                val i = incoming.single()
                val o = outgoing.single()
                (i.value as TSequenceFlow).targetRef = (o.value as TSequenceFlow).targetRef
                flowElements.remove(o)
                flowElements.removeIf { (it.value as? TTask) == task }
            }
        }
    }

    /**
     * Computes and returns a BPMN model corresponding to the [ProcessTree] passed to the constructor
     */
    fun toBPMN(): BPMNModel {
        val (s, e) = convert(checkNotNull(tree.root))
        val startEvent = add(TStartEvent())
        val endEvent = add(TEndEvent())
        link(startEvent, s)
        link(e, endEvent)
        pruneSilents()
        return finish()
    }
}

/**
 * Returns a BPMN model corresponding to the process tree.
 * The conversion is sound, but not necessarily optimal.
 */
fun ProcessTree.toBPMN(): BPMNModel = ProcessTree2BPMN(this).toBPMN()