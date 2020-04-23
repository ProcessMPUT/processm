package processm.core.models.bpmn

import processm.core.helpers.allSubsets
import processm.core.logging.logger
import processm.core.models.bpmn.jaxb.*
import processm.core.models.commons.Activity
import processm.core.models.commons.DecisionPoint

/**
 * A base class for warappers of [TFlowNode].
 *
 * It is a [DecisionPoint], because an Activity in BPMN can contain a split with decisions (por. sec. 13.2.1 of BPMN spec.)
 */
abstract class BPMNFlowNode internal constructor(override val name:String, internal val process: BPMNProcess) : Activity {

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

    //TODO probably write separate code for BPMNEvent and move this to BPMNActivity
    open val split: BPMNDecisionPoint by lazy {
        val result = BPMNDecisionPoint(this)
        val boundaryEvents = nodes(process.boundaryEventsFor(this@BPMNFlowNode))
        val default = nodes(listOfNotNull((base as? TActivity)?.default).filterIsInstance<TSequenceFlow>().map { it.targetRef as TFlowNode })
        val (tmpcond, tmpuncond) = outgoingSequenceFlows.partition { it.conditionExpression != null }
        val conditional = nodes(tmpcond.map { it.targetRef as TFlowNode })
        val unconditional = nodes(tmpuncond.map { it.targetRef as TFlowNode }) - default
        logger().debug("#conditional ${conditional.size} #unconditional ${unconditional.size} #default ${default.size} #events ${boundaryEvents.size}")
        /*
        1. Each boundary event can occur alone
        2. All unconditional + (any non-empty subset of conditional)
        3. All unconditional + default
         */
        for (event in boundaryEvents)
            result.add(setOf(event))
        for (subset in conditional.allSubsets().filter { it.isNotEmpty() })
            result.add(subset.toSet() + unconditional)
        if (unconditional.isNotEmpty() || default.isNotEmpty())
            result.add(unconditional + default)
        return@lazy result
    }

    open val join: BPMNDecisionPoint by lazy {
        val result = BPMNDecisionPoint(this)
        for (flow in incomingSequenceFlows)
            result.add(nodes(setOf(flow.sourceRef as TFlowNode)))
        return@lazy result
    }

    internal fun hasBase(other: TBaseElement) = base === other

}