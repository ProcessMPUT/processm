package processm.core.models.bpmn

import processm.core.helpers.allSubsets
import processm.core.models.bpmn.jaxb.*

/**
 * A wrapper for [TGateway].
 */
class BPMNGateway internal constructor(override val base: TGateway, name: String, process: BPMNProcess) : BPMNFlowNode(name, process) {

    override val split: BPMNDecisionPoint by lazy {
        val result = BPMNDecisionPoint(this)
        val targets = outgoingSequenceFlows.map { it.targetRef as TFlowNode }
        if (base is TExclusiveGateway || (base is TEventBasedGateway && base.eventGatewayType == TEventBasedGatewayType.EXCLUSIVE)) {
            for (it in targets)
                result.add(nodes(listOf(it)))
        } else if (base is TInclusiveGateway) {
            val default = listOfNotNull(base.default).filterIsInstance<TSequenceFlow>().map { it.targetRef as TFlowNode }
            for (it in (targets - default).allSubsets().filter { it.isNotEmpty() })
                result.add(nodes(it))
            if (default.isNotEmpty())
                result.add(nodes(default))
        } else if (base is TParallelGateway || (base is TEventBasedGateway && base.eventGatewayType == TEventBasedGatewayType.PARALLEL)) {
            result.add(nodes(targets))
        } else {
            throw IllegalArgumentException("A gateway of unknown type ${base::class}")
        }
        return@lazy result
    }

    override val join: BPMNDecisionPoint by lazy {
        val result = BPMNDecisionPoint(this)
        val nodes = nodes(incomingSequenceFlows.map { it.sourceRef as TFlowNode })
        if (base is TParallelGateway || (base is TEventBasedGateway && base.eventGatewayType == TEventBasedGatewayType.PARALLEL)) {
            result.add(nodes)
            return@lazy result
        } else {
            for (n in nodes)
                result.add(setOf(n))
            return@lazy result
        }
    }

}