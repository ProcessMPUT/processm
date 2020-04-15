package processm.core.models.bpmn

import processm.core.models.bpmn.converters.BPMN2CausalNet
import processm.core.models.bpmn.jaxb.*
import processm.core.models.causalnet.MutableCausalNet
import java.util.*
import javax.xml.bind.JAXBElement
import javax.xml.namespace.QName

internal inline fun hasId(node: TBaseElement, qname: QName) = node.id == qname.localPart

/**
 * A wrapper for [TProcess]
 */
internal class BPMNProcess internal constructor(internal val base: TProcess) {


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

    internal val recursiveArtifacts: Sequence<TArtifact> = sequence {
        val queue = ArrayDeque<List<JAXBElement<out TFlowElement>>>()
        queue.add(base.flowElement)
        yieldAll(base.artifact.map { it.value })
        while (queue.isNotEmpty()) {
            val fe = queue.poll().map { it.value }
            for (sp in fe.filterIsInstance<TSubProcess>()) {
                yieldAll(sp.artifact.map { it.value })
                queue.add(sp.flowElement)
            }
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
            flowNodes.asSequence().filter { it.base is TStartEvent || (it.base.incoming.isEmpty() && it.base !is TBoundaryEvent) }

    /**
     * Note we consistently ignore triggers of end events. We also do not look into subprocesses, so we report a subprocess instead of an end event of the subprocess.
     */
    val endActivities: Sequence<BPMNFlowNode> =
            flowNodes.asSequence().filter { it.base is TEndEvent || it.base.outgoing.isEmpty() }

    internal val associations = base.artifact.filterIsInstance<TAssociation>()

    internal inline fun byName(qname: QName) = recursiveFlowElements.filter { hasId(it, qname) }

    internal inline fun <reified ExpectedType> flowByName(qname: QName): ExpectedType {
        try {
            return byName(qname).filterIsInstance<ExpectedType>().single()
        } catch (e: NoSuchElementException) {
            println("Missing $qname")
            throw e
        }
    }

    internal fun eventsFor(node: BPMNFlowNode): Sequence<TBoundaryEvent> =
            recursiveFlowElements
                    .filterIsInstance<TBoundaryEvent>()
                    .filter { event -> hasId(node.base, event.attachedToRef) }

    fun get(base: TFlowNode) =
            allActivities.single { it.base === base }

    fun toCausalNet(): MutableCausalNet = BPMN2CausalNet(this).convert()

}