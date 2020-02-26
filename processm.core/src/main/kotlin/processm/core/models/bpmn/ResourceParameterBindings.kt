package processm.core.models.bpmn

import processm.core.models.bpmn.foundation.BaseElement

class ResourceParameterBindings(id:String, val parameterRef: ResourceParameter, val expression: Expression):
    BaseElement(id) {

}
