package processm.core.models.bpmn

import processm.core.models.commons.AbstractDecision

class Decision(val activities: List<BPMNFlowNode>, override val decisionPoint: BPMNGateway) :AbstractDecision {

}