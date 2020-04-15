package processm.core.models.bpmn

import processm.core.models.bpmn.jaxb.TEvent

/**
 * A public wrapper for [TEvent]
 */
internal class BPMNEvent internal constructor(override val base: TEvent, process: BPMNProcess) : BPMNFlowNode(process) {
    override val name: String by lazy {
        if (!base.name.isNullOrBlank())
            return@lazy base.name
        if (!base.id.isNullOrBlank())
            return@lazy "ID:${base.id}"
        val idx = process.flowElements.indexOf(base)
        check(idx >= 0)
        return@lazy "IDX:${idx}"
    }
}