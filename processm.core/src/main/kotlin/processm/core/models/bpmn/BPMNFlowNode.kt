package processm.core.models.bpmn

import processm.core.models.bpmn.jaxb.TFlowNode
import processm.core.models.commons.AbstractActivity

/**
 * A base class for warappers of [TFlowNode]
 */
abstract class BPMNFlowNode : AbstractActivity {

    internal abstract val base: TFlowNode
}