package processm.core.models.bpmn

import processm.core.models.commons.Decision

/**
 * A decision that can be made in [decisionPoint], resulting in executing [activities]
 */
data class BPMNDecision(val activities: Set<BPMNFlowNode>, override val decisionPoint: BPMNDecisionPoint) : Decision