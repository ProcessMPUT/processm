package processm.miners.bpmn

import org.jetbrains.exposed.sql.Database
import processm.core.log.hierarchical.DBHierarchicalXESInputStream
import processm.core.models.bpmn.BPMNModel
import processm.core.models.bpmn.DBSerializer
import processm.core.models.bpmn.converters.toBPMN
import processm.core.models.bpmn.toXML
import processm.core.models.causalnet.CausalNet
import processm.core.models.processtree.ProcessTree
import processm.dbmodels.models.ComponentTypeDto
import processm.dbmodels.models.WorkspaceComponent
import processm.miners.AbstractMinerService
import processm.miners.CalcJob
import processm.miners.DeleteJob
import processm.miners.MinerJob
import java.util.*


/**
 * The service that discovers a BPMN model from the event log.
 */
class BPMNMinerService : AbstractMinerService(
    QUARTZ_CONFIG,
    ComponentTypeDto.BPMN,
    CalcBPMNJob::class.java,
    DeleteBPMNJob::class.java
) {
    companion object {
        private const val QUARTZ_CONFIG = "quartz-bpmn.properties"
    }

    override val name: String
        get() = "BPMN"

    interface BPMNJob : MinerJob<BPMNModel> {
        override fun mine(component: WorkspaceComponent, stream: DBHierarchicalXESInputStream): BPMNModel {

            val miner = minerFromProperties(component.properties)


            val model = miner.processLog(stream)

            return when (model) {
                is ProcessTree -> model.toBPMN()
                is CausalNet -> model.toBPMN()
                else -> throw IllegalStateException("Unexpected type of process model: ${model.javaClass.name}")
            }
        }

        override fun delete(database: Database, id: String) {
            DBSerializer.delete(database, UUID.fromString(id))
        }

        override fun store(database: Database, model: BPMNModel): String =
            DBSerializer.insert(database, model.toXML()).toString()
    }

    class CalcBPMNJob : CalcJob<BPMNModel>(), BPMNJob

    class DeleteBPMNJob : DeleteJob<BPMNModel>(), BPMNJob
}
