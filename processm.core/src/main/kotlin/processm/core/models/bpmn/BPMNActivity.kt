package processm.core.models.bpmn

import processm.core.models.bpmn.jaxb.TActivity
import processm.core.models.commons.AbstractActivity

class BPMNActivity(private val base: TActivity) : AbstractActivity {
    override val name: String
        get() = base.name

    override fun toString(): String = "BPMNActivity($name)"
}