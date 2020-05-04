package processm.core.models.bpmn

import processm.core.models.bpmn.jaxb.TEvent

/**
 * A public wrapper for [TEvent]
 */
internal class BPMNEvent internal constructor(override val base: TEvent) : BPMNFlowNode() {
    override val name: String = base.name ?: ""
}