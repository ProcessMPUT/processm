package processm.core.models.bpmn

import processm.core.models.bpmn.jaxb.TDefinitions
import processm.core.models.bpmn.jaxb.TProcess
import processm.core.models.causalnet.MutableModel
import processm.core.models.commons.AbstractModel
import processm.core.models.commons.AbstractModelInstance
import java.io.InputStream

/**
 * An entry point for processing a BPMN file. Aggregates multiple BPMN models into a single object.
 */
class BPMNModel internal constructor(internal val model: TDefinitions) : AbstractModel {

    companion object {
        /**
         * Constructs [BPMNModel] from the given BPMN XML
         */
        fun fromXML(xml: InputStream): BPMNModel =
            BPMNModel(BPMNXMLService.load(xml).value)
    }

    private val processes: List<BPMNProcess> =
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
     * Lists all decisions points of the model, i.e., all the gateways. Some of them may not be strict decision points.
     */
    override val decisionPoints: Sequence<BPMNGateway>
        get() = processes.asSequence().flatMap { it.allActivities.asSequence().filterIsInstance<BPMNGateway>() }

    /**
     * Instances of BPMN models are currently not supported, as BPMN support is for import/export only.
     */
    override fun createInstance(): AbstractModelInstance =
        throw UnsupportedOperationException("BPMN model instances are not supported")

    fun toCausalNet(): MutableModel {
        require(processes.size == 1) { "BPMN models with multiple processes are currently not supported" }
        return processes.single().toCausalNet()
    }
}