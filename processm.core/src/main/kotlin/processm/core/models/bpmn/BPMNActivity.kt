package processm.core.models.bpmn

import processm.core.models.bpmn.jaxb.TActivity

/**
 * A public wrapper for [TActivity]
 */
internal class BPMNActivity internal constructor(override val base: TActivity) : BPMNFlowNode() {
    override val name: String
        get() = base.name

    override fun toString(): String = "BPMNActivity($name)"
}