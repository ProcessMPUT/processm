package processm.core.models.bpmn

import processm.core.models.bpmn.jaxb.TFlowNode
import processm.core.models.commons.Activity

/**
 * A base class for warappers of [TFlowNode]
 */
abstract class BPMNFlowNode : Activity {

    internal abstract val base: TFlowNode
}