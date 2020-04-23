package processm.core.models.bpmn

import processm.core.models.bpmn.jaxb.*
import processm.core.models.commons.Activity
import processm.core.models.commons.DecisionPoint

/**
 * A base class for warappers of [TFlowNode].
 *
 * It is a [DecisionPoint], because an Activity in BPMN can contain a split with decisions (por. sec. 13.2.1 of BPMN spec.)
 */
abstract class BPMNFlowNode internal constructor(override val name: String, internal val process: BPMNProcess) : Activity {

    protected abstract val base: TFlowNode

    override fun equals(other: Any?): Boolean {
        if (other is BPMNFlowNode)
            return this.base === other.base && this.process === other.process
        else
            return super.equals(other)
    }

    override fun hashCode(): Int = base.hashCode()

    protected val outgoingSequenceFlows
        get() = base.outgoing.map { process.flowByName<TSequenceFlow>(it) }

    protected val incomingSequenceFlows
        get() = base.incoming.map { process.flowByName<TSequenceFlow>(it) }

    protected fun nodes(inp: Collection<TFlowNode>) = inp.map { process.get(it) }.toSet()

    internal val isStart
        get() = base is TStartEvent || (base.incoming.isEmpty() && base !is TBoundaryEvent)
    internal val isEnd
        get() = base is TEndEvent || base.outgoing.isEmpty()
    internal val isComposite
        get() = base is TSubProcess
    internal val children
        get() = (base as? TSubProcess)?.flowElement.orEmpty().map { it.value }.filterIsInstance<TFlowNode>().map { process.get(it) }
    internal val id
        get() = base.id

    /**
     * The decision point representing leaving this node
     */
    abstract val split: BPMNDecisionPoint

    /**
     * The decision point representing coming to this node
     */
    abstract val join: BPMNDecisionPoint

    internal fun hasBase(other: TBaseElement) = base === other

}