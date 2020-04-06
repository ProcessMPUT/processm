package processm.core.models.bpmn

import processm.core.models.bpmn.jaxb.*
import processm.core.models.commons.AbstractActivity
import java.util.*
import javax.xml.bind.JAXBElement

class BPMNProcess(base: TProcess) {

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

    val flowElements = base.flowElement.asSequence().map { it.value }
    val allActivities = recursiveFlowElements.filterIsInstance<TActivity>().map { BPMNActivity(it) }
    val allEvents = recursiveFlowElements.filterIsInstance<TEvent>().map { BPMNEvent(it) }
    val allGateways = recursiveFlowElements.filterIsInstance<TGateway>().map { BPMNGateway(it) }


    /**
     * Note we consistently ignore triggers of start events. We also do not look into subprocesses, so we report a subprocess instead of a start event of the subprocess.
     */
    val startActivities: Sequence<AbstractActivity> =
        flowElements.filterIsInstance<TStartEvent>().map { BPMNEvent(it) } +
                flowElements.filterIsInstance<TActivity>().filter { it.incoming.isEmpty() }.map { BPMNActivity(it) }

    /**
     * Note we consistently ignore triggers of end events. We also do not look into subprocesses, so we report a subprocess instead of an end event of the subprocess.
     */
    val endActivities: Sequence<AbstractActivity> =
        flowElements.filterIsInstance<TEndEvent>().map { BPMNEvent(it) } +
                flowElements.filterIsInstance<TActivity>().filter { it.outgoing.isEmpty() }.map { BPMNActivity(it) }
}