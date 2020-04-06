package processm.core.models.bpmn

import processm.core.models.bpmn.jaxb.TEvent
import processm.core.models.commons.AbstractActivity

class BPMNEvent(internal val event: TEvent) : AbstractActivity {
    //TODO possibly introduce BPMNSilentEvent or something linke that to distinguish events with no names
    override val name: String = event.name ?: ""
}