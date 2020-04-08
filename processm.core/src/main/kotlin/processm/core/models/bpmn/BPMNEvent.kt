package processm.core.models.bpmn

import processm.core.models.bpmn.jaxb.TEvent

class BPMNEvent(override val base: TEvent) : BPMNFlowNode() {
    override val name: String = base.name ?: ""
}