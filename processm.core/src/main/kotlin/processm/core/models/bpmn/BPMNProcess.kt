package processm.core.models.bpmn

import processm.core.models.bpmn.jaxb.*
import java.util.*
import javax.xml.bind.JAXBElement
import javax.xml.namespace.QName

class BPMNProcess(base: TProcess) {

    val activities: Sequence<BPMNActivity> =
        sequence {
            val queue = ArrayDeque<List<JAXBElement<out TFlowElement>>>()
            queue.add(base.flowElement)
            while (queue.isNotEmpty()) {
                val fe = queue.poll()
                for (a in fe.map { it.value }.filterIsInstance<TActivity>()) {
                    yield(BPMNActivity(a))
                    if (a is TSubProcess)
                        queue.add(a.flowElement)
                }
            }
        }

    val startActivities: Sequence<BPMNActivity> = sequence {
        val startEvents = base.flowElement.map { it.value }.filterIsInstance<TStartEvent>()
        if (startEvents.isNotEmpty()) {
            for (start in startEvents)
                yieldAll(start
                    .outgoing
                    .asSequence()
                    .flatMap { qname ->
                        base
                            .flowElement
                            .asSequence()
                            .filter { QName(it.value.id) == qname }
                            .map { it.value }
                            .filterIsInstance<TSequenceFlow>()
                            .map { it.targetRef }
                            .filterIsInstance<TActivity>()
                            .map { BPMNActivity(it) }
                    })
        } else {
            TODO("Technically speaking a process doesn't have to have a starting event, but I dunno what to do if one doesn't")
        }
    }

    val endActivities: Sequence<BPMNActivity> = sequence {
        val endEvents = base.flowElement.map { it.value }.filterIsInstance<TEndEvent>()
        if (endEvents.isNotEmpty()) {
            for (end in endEvents)
                yieldAll(end
                    .incoming
                    .asSequence()
                    .flatMap { qname ->
                        base
                            .flowElement
                            .asSequence()
                            .filter { QName(it.value.id) == qname }
                            .map { it.value }
                            .filterIsInstance<TSequenceFlow>()
                            .map { it.sourceRef }
                            .filterIsInstance<TActivity>()
                            .map { BPMNActivity(it) }
                    })
        } else {
            TODO("Technically speaking a process doesn't have to have an end event, but I dunno what to do if one doesn't")
        }
    }

}