package processm.core.models.bpmn

import processm.core.helpers.allSubsets
import processm.core.logging.logger
import processm.core.models.bpmn.jaxb.TActivity
import processm.core.models.bpmn.jaxb.TCallActivity
import processm.core.models.bpmn.jaxb.TFlowNode
import processm.core.models.bpmn.jaxb.TSequenceFlow

/**
 * A wrapper for [TActivity]. Only its base class is public.
 */
internal class BPMNActivity internal constructor(override val base: TActivity, name: String, process: BPMNProcess) : BPMNFlowNode(name, process) {

    val isForCompensation
        get() = base.isIsForCompensation

    val isCallable
        get() = base is TCallActivity

    override val split: BPMNDecisionPoint by lazy(LazyThreadSafetyMode.NONE) {
        val result = BPMNDecisionPoint(this)
        val boundaryEvents = nodes(process.boundaryEventsFor(this@BPMNActivity))
        val default = nodes(listOfNotNull(base.default as? TSequenceFlow).map { it.targetRef as TFlowNode })
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


    override val join: BPMNDecisionPoint by lazy(LazyThreadSafetyMode.NONE) {
        val result = BPMNDecisionPoint(this)
        for (flow in incomingSequenceFlows)
            result.add(nodes(setOf(flow.sourceRef as TFlowNode)))
        return@lazy result
    }
}