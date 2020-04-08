package processm.core.models.bpmn

import processm.core.models.bpmn.jaxb.TEvent

class BPMNEvent(override val base: TEvent) : BPMNFlowNode() {
    //TODO possibly introduce BPMNSilentEvent or something linke that to distinguish events with no names
    override val name: String = base.name ?: ""
}