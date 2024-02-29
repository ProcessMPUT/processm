package processm.core.models.bpmn

import jakarta.xml.bind.JAXBElement
import processm.core.models.bpmn.jaxb.*
import processm.logging.logger
import java.util.*
import javax.xml.namespace.QName

internal inline fun hasId(node: BPMNFlowNode, qname: QName) = hasId(node.id, qname)
internal inline fun hasId(id: String, qname: QName) = id == qname.localPart

/**
 * A wrapper for [TProcess]
 */
internal class BPMNProcess internal constructor(internal val base: TProcess) {

    private val recursiveFlowElements: List<TFlowElement> by lazy {
        val result = ArrayList<TFlowElement>()
        val queue = ArrayDeque<List<JAXBElement<out TFlowElement>>>()
        queue.add(base.flowElement)
        while (queue.isNotEmpty()) {
            val fe = queue.poll().map { it.value }
            result.addAll(fe)
            for (sp in fe.filterIsInstance<TSubProcess>())
                queue.add(sp.flowElement)
        }
        return@lazy result
    }

    private fun name(base: TFlowNode): String {
        if (!base.name.isNullOrBlank())
            return base.name
        if (!base.id.isNullOrBlank())
            return "ID:${base.id}"
        val idx = recursiveFlowElements.indexOf(base)
        check(idx >= 0) { "An element not found in recursiveFlowElements." }
        return "IDX:${idx}"
    }

    private fun create(inp: TFlowNode): BPMNFlowNode {
        val name = name(inp)
        return when (inp) {
            is TEvent -> BPMNEvent(inp, name, this)
            is TActivity -> BPMNActivity(inp, name, this)
            is TGateway -> BPMNGateway(inp, name, this)
            else -> throw IllegalArgumentException("Cannot handle ${inp.javaClass}")
        }
    }

    private val flowNodes by lazy { base.flowElement.map { it.value }.filterIsInstance<TFlowNode>().map { get(it) }.toList() }
    val allActivities = recursiveFlowElements.filterIsInstance<TFlowNode>().map { create(it) }.toList()


    /**
     * Note we consistently ignore triggers of start events. We also do not look into subprocesses, so we report a subprocess instead of a start event of the subprocess.
     */
    val startActivities: Sequence<BPMNFlowNode> =
            flowNodes.asSequence().filter { it.isStart }

    /**
     * Note we consistently ignore triggers of end events. We also do not look into subprocesses, so we report a subprocess instead of an end event of the subprocess.
     */
    val endActivities: Sequence<BPMNFlowNode> =
            flowNodes.asSequence().filter { it.isEnd }

    internal inline fun byName(qname: QName) = recursiveFlowElements.filter { hasId(it.id, qname) }

    internal inline fun <reified ExpectedType> flowByName(qname: QName): ExpectedType {
        try {
            return byName(qname).filterIsInstance<ExpectedType>().single()
        } catch (e: NoSuchElementException) {
            logger().warn("Missing $qname")
            throw e
        }
    }

    internal fun boundaryEventsFor(node: BPMNFlowNode): List<TBoundaryEvent> =
            recursiveFlowElements
                    .filterIsInstance<TBoundaryEvent>()
                    .filter { event -> hasId(node, event.attachedToRef) }

    fun get(base: TFlowNode) =
            allActivities.single { it.hasBase(base) }
}
