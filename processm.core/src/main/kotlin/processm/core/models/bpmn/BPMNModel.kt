package processm.core.models.bpmn

import processm.core.models.bpmn.jaxb.TDefinitions
import processm.core.models.bpmn.jaxb.TProcess
import processm.core.models.commons.AbstractActivity
import processm.core.models.commons.AbstractModel
import java.io.InputStream

class BPMNModel internal constructor(private val model: TDefinitions) : AbstractModel {

    companion object {
        fun fromXML(xml: InputStream): BPMNModel =
            BPMNModel(BPMNXMLService.load(xml).value)
    }

    private val processes: List<BPMNProcess> =
        model.rootElement.map { it.value }.filterIsInstance<TProcess>().map { BPMNProcess(it) }

    override val activities: Sequence<BPMNActivity>
        get() = processes.asSequence().flatMap { it.activities }

    override val startActivities: Sequence<AbstractActivity>
        get() = processes.asSequence().flatMap { it.startActivities }

    override val endActivities: Sequence<AbstractActivity>
        get() = processes.asSequence().flatMap { it.endActivities }

}