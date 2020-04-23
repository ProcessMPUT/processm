package processm.core.models.bpmn

import processm.core.helpers.allSubsets
import processm.core.models.bpmn.jaxb.TBoundaryEvent
import processm.core.models.bpmn.jaxb.TEndEvent
import processm.core.models.bpmn.jaxb.TEvent
import processm.core.models.bpmn.jaxb.TFlowNode

/**
 * A wrapper for [TEvent]. Only its base class is public.
 */
internal class BPMNEvent internal constructor(override val base: TEvent, name: String, process: BPMNProcess) : BPMNFlowNode(name, process) {

    override val join: BPMNDecisionPoint by lazy {
        if (base is TEndEvent) {
            //BPMN spec., ch. 10.4.3: All the tokens that were generated within the Process MUST be consumed by an End Event before the Process has been completed.
            val result = BPMNDecisionPoint(this)
            val nodes = nodes(incomingSequenceFlows.map { it.sourceRef as TFlowNode })
            for (subset in nodes.allSubsets().filter { it.isNotEmpty() })
                result.add(subset.toSet())
            return@lazy result
        } else if (base is TBoundaryEvent) {
            check(incomingSequenceFlows.isEmpty())
            val result = BPMNDecisionPoint(this)
            val nodes = nodes(process.byName(base.attachedToRef).filterIsInstance<TFlowNode>())
            result.add(BPMNDecision(nodes, result))
            return@lazy result
        } else
            return@lazy super.join
    }
}