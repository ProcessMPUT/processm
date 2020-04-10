package processm.core.models.bpmn

import processm.core.models.bpmn.jaxb.TFlowNode
import processm.core.models.bpmn.jaxb.TSequenceFlow
import processm.core.models.commons.Activity

/**
 * A base class for warappers of [TFlowNode]
 */
abstract class BPMNFlowNode internal constructor(internal val process: BPMNProcess) : Activity {

    internal abstract val base: TFlowNode

    override fun equals(other: Any?): Boolean {
        if (other is BPMNFlowNode)
            return this.base === other.base && this.process === other.process
        else
            return super.equals(other)
    }

    override fun hashCode(): Int = base.hashCode()

    internal val outgoing by lazy {
        base.outgoing.map { process.get(process.flowByName<TSequenceFlow>(it).targetRef as TFlowNode) }
    }
    internal val incoming by lazy {
        base.incoming.map { process.get(process.flowByName<TSequenceFlow>(it).sourceRef as TFlowNode) }
    }
    internal val isSplit by lazy {
        outgoing.size > 1
    }
    internal val isJoin by lazy {
        incoming.size > 1
    }
}