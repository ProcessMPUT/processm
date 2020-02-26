package processm.core.models.bpmn

import processm.core.models.bpmn.foundation.BaseElement

class ResourceRole(id: String) : BaseElement(id) {
    var resourceRef: Resource? = null
        internal set(v) =
            if ((v == null) || (resourceAssignmentExpression == null)) field = v
            else throw IllegalStateException()
    var resourceAssignmentExpression: ResourceAssignmentExpression? = null
        internal set(v) =
            if ((v == null) || (resourceRef == null)) field = v
            else throw IllegalStateException()
    val resourceParameterBindings: List<ResourceParameterBindings> = ArrayList()
}
