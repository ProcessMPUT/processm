package processm.core.models.bpmn

import processm.core.helpers.allSubsets
import processm.core.models.bpmn.jaxb.*
import processm.core.models.commons.AbstractDecisionPoint

/**
 * A wrapper for [TGateway]. Implements [AbstractDecisionPoint], but not resulting decision points are strict
 */
class BPMNGateway internal constructor(override val base: TGateway, internal val process: BPMNProcess) : BPMNFlowNode(), AbstractDecisionPoint {

    override val name: String
        get() = base.name

    override val possibleOutcomes: List<Decision> by lazy {
        val outgoing =
            base.outgoing.map { process.get(process.flowByName<TSequenceFlow>(it).targetRef as TFlowNode) }
        val incoming =
            base.incoming.map { process.get(process.flowByName<TSequenceFlow>(it).sourceRef as TFlowNode) }
        val isSplit = outgoing.size > 1
        val isJoin = incoming.size > 1
        check(isSplit xor isJoin) {
            "BPMN spec.: \"A Gateway MUST have either multiple incoming Sequence Flows or multiple outgoing Sequence Flows\n" +
                    "        (i.e., it MUST merge or split the flow).\""
        }
        when (base) {
            is TExclusiveGateway -> {
                if (isSplit)
                    return@lazy outgoing.map { Decision(listOf(it), this) }.toMutableList()
                else
                /*BPMN spec: A converging Exclusive Gateway is used to merge alternative paths. Each incoming Sequence Flow token is routed
to the outgoing Sequence Flow without synchronization.*/
                    return@lazy emptyList<Decision>()
            }
            is TInclusiveGateway -> {
                /*BPMN spec: Since each path is considered to be independent, all combinations of the paths MAY be taken,
                from zero to all. However, it should be designed so that at least one path is taken.
                 */
                if (isSplit) {
                    //Note: we ignore conditions and simply generate the powerset of possible outcomes.
                    return@lazy outgoing.allSubsets().map { Decision(it, this) }.toList()
                } else
                    throw NotImplementedError("Specification on this behaviour seems to be recursive and I don't know how to proceed here")
            }
            is TParallelGateway -> {
                /* BPMN spec: A Parallel Gateway creates parallel paths without checking any conditions; each outgoing Sequence Flow receives a
token upon execution of this Gateway. For incoming flows, the Parallel Gateway will wait for all incoming flows
before triggering the flow through its outgoing Sequence Flows.*/
                return@lazy emptyList<Decision>()
            }
            is TEventBasedGateway -> {
                /* BPMN spec: Basically, the decision is made by another Participant, based on data that is not visible to Process, thus,
requiring the use of the Event-Based Gateway.  (...) When the first Event in the Event Gateway configuration is triggered, then the path that follows that Event will used
(a token will be sent down the Event’s outgoing Sequence Flows). All the remaining paths of the Event Gateway
configuration will no longer be valid. Basically, the Event Gateway configuration is a race condition where the first
Event that is triggered wins.(...)
The Parallel Event Gateway is also a type of race condition. In this case, however, when the first Event is triggered
and the Process is instantiated, the other Events of the Gateway configuration are not disabled. The other Events are
still waiting and are expected to be triggered before the Process can (normally) complete. In this case, the Messages
that trigger the Events of the Gateway configuration MUST share the same correlation information.
*/
                return@lazy if (base.eventGatewayType == TEventBasedGatewayType.EXCLUSIVE) {
                    outgoing.map { Decision(listOf(it), this) }
                } else {
                    emptyList<Decision>()
                }
            }
            else -> throw IllegalArgumentException("A gateway of unknown type ${base::class}")
        }
    }


}