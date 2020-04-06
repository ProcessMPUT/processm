package processm.core.models.bpmn

import processm.core.models.bpmn.jaxb.TGateway
import processm.core.models.commons.AbstractDecisionPoint

class BPMNGateway(internal val base: TGateway):AbstractDecisionPoint {
    /**
     * BPMN spec.: "A Gateway MUST have either multiple incoming Sequence Flows or multiple outgoing Sequence Flows
    (i.e., it MUST merge or split the flow)."
     */
    override val isStrict: Boolean = true
}