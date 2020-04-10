package processm.core.models.bpmn

import processm.core.helpers.allSubsets
import processm.core.models.bpmn.jaxb.*
import processm.core.models.causalnet.*
import processm.core.models.causalnet.Node
import java.util.*
import javax.xml.bind.JAXBElement
import javax.xml.namespace.QName

/**
 * A wrapper for [TProcess]
 */
internal class BPMNProcess internal constructor(base: TProcess) {

    internal val recursiveFlowElements: Sequence<TFlowElement> = sequence {
        val queue = ArrayDeque<List<JAXBElement<out TFlowElement>>>()
        queue.add(base.flowElement)
        while (queue.isNotEmpty()) {
            val fe = queue.poll().map { it.value }
            yieldAll(fe)
            for (sp in fe.filterIsInstance<TSubProcess>())
                queue.add(sp.flowElement)
        }
    }

    private fun create(inp: TFlowNode): BPMNFlowNode =
        when (inp) {
            is TEvent -> BPMNEvent(inp, this)
            is TActivity -> BPMNActivity(inp, this)
            is TGateway -> BPMNGateway(inp, this)
            else -> throw IllegalArgumentException("Cannot handle ${inp.javaClass}")
        }

    val flowElements = base.flowElement.map { it.value }
    val flowNodes = flowElements.filterIsInstance<TFlowNode>().map { create(it) }.toList()
    val allActivities = recursiveFlowElements.filterIsInstance<TFlowNode>().map { create(it) }.toList()


    /**
     * Note we consistently ignore triggers of start events. We also do not look into subprocesses, so we report a subprocess instead of a start event of the subprocess.
     */
    val startActivities: Sequence<BPMNFlowNode> =
        flowNodes.asSequence().filter { it.base is TStartEvent || it.base.incoming.isEmpty() }

    /**
     * Note we consistently ignore triggers of end events. We also do not look into subprocesses, so we report a subprocess instead of an end event of the subprocess.
     */
    val endActivities: Sequence<BPMNFlowNode> =
        flowNodes.asSequence().filter { it.base is TEndEvent || it.base.outgoing.isEmpty() }

    internal inline fun <reified ExpectedType> flowByName(qname: QName) =
        flowElements.filter { it.id == qname.toString() }.filterIsInstance<ExpectedType>().single()

    fun get(base: TFlowNode) =
        allActivities.single { it.base === base }

    fun toCausalNet(): MutableCausalNet = BPMN2CausalNet(this).convert()

}