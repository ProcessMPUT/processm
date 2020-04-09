package processm.core.models.bpmn

import processm.core.models.bpmn.jaxb.TEvent

/**
 * A public wrapper for [TEvent]
 */
internal class BPMNEvent internal constructor(override val base: TEvent, process: BPMNProcess) : BPMNFlowNode(process) {
    override val name: String = base.name ?: ""
}