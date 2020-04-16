package processm.core.models.bpmn

import processm.core.models.bpmn.jaxb.TActivity
import processm.core.models.bpmn.jaxb.TCallActivity

/**
 * A wrapper for [TActivity]. Only its base class is public.
 */
internal class BPMNActivity internal constructor(override val base: TActivity, name: String, process: BPMNProcess) : BPMNFlowNode(name, process) {

    val isForCompensation
        get() = base.isIsForCompensation

    val isCallable
        get() = base is TCallActivity
}