package processm.core.models.bpmn

import processm.core.models.commons.Decision

/**
 * A decision that can be made in [decisionPoint], resulting in executing [activities]
 */
class Decision(val activities: List<BPMNFlowNode>, override val decisionPoint: BPMNGateway) : Decision