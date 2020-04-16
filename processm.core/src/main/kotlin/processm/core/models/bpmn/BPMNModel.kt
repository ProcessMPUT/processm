package processm.core.models.bpmn

import processm.core.models.bpmn.converters.BPMNModel2CausalNet
import processm.core.models.bpmn.jaxb.TDefinitions
import processm.core.models.bpmn.jaxb.TFlowNode
import processm.core.models.bpmn.jaxb.TProcess
import processm.core.models.causalnet.MutableCausalNet
import processm.core.models.commons.ProcessModel
import processm.core.models.commons.ProcessModelInstance
import java.io.InputStream
import javax.xml.namespace.QName

/**
 * An entry point for processing a BPMN file. Aggregates multiple BPMN models into a single object.
 */
class BPMNModel internal constructor(internal val model: TDefinitions) : ProcessModel {

    companion object {
        /**
         * Constructs [BPMNModel] from the given BPMN XML
         */
        fun fromXML(xml: InputStream): BPMNModel =
                BPMNModel(BPMNXMLService.load(xml).value)
    }

    internal val processes: List<BPMNProcess> =
            model.rootElement.map { it.value }.filterIsInstance<TProcess>().map { BPMNProcess(it) }

    /**
     * Lists all TActivities, TEvents and TGateways of the underlying models, incl. these in subprocesses
     */
    override val activities: Sequence<BPMNFlowNode>
        get() = processes.asSequence().flatMap { it.allActivities.asSequence() }

    /**
     * Lists all start activities of the underlying process, i.e., TActivities without any incoming sequence flows and TStartEvents.
     * Does not include activities of subprocesses.
     */
    override val startActivities: Sequence<BPMNFlowNode>
        get() = processes.asSequence().flatMap { it.startActivities }

    /**
     * Lists all end activities of the underlying process, i.e., TActivities without any outgoing sequence flows and TEndEvents.
     * Does not include activities of subprocesses.
     */
    override val endActivities: Sequence<BPMNFlowNode>
        get() = processes.asSequence().flatMap { it.endActivities }

    /**
     * Lists all decisions points of the model, i.e., all the gateways. Some of them may be not real decision points.
     */
    override val decisionPoints: Sequence<BPMNDecisionPoint>
        get() = processes.asSequence().flatMap { proc -> proc.allActivities.asSequence().map { it.split } }

    /**
     * Instances of BPMN models are currently not supported, as BPMN support is for import/export only.
     */
    override fun createInstance(): ProcessModelInstance =
            throw UnsupportedOperationException("BPMN model instances are not supported")

    internal fun byName(name: QName) = processes
            .flatMap { p ->
                p.byName(name)
                        .filterIsInstance<TFlowNode>()
                        .map { p to p.get(it) }
            }
            .single()

    fun toCausalNet(): MutableCausalNet = BPMNModel2CausalNet(this).toCausalNet()
}