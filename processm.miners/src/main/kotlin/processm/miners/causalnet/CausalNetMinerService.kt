package processm.miners.causalnet

import org.jetbrains.exposed.sql.Database
import processm.core.log.hierarchical.DBHierarchicalXESInputStream
import processm.core.models.causalnet.CausalNet
import processm.core.models.causalnet.DBSerializer
import processm.core.models.causalnet.converters.toCausalNet
import processm.core.models.processtree.ProcessTree
import processm.dbmodels.models.ComponentTypeDto
import processm.dbmodels.models.WorkspaceComponent
import processm.miners.AbstractMinerService
import processm.miners.CalcJob
import processm.miners.DeleteJob

/**
 * The service that discovers Causal net from the event log.
 */
class CausalNetMinerService : AbstractMinerService(
    QUARTZ_CONFIG,
    ComponentTypeDto.CausalNet,
    CalcCNetJob::class.java,
    DeleteCNetJob::class.java
) {
    companion object {
        private const val QUARTZ_CONFIG = "quartz-causalnet.properties"
    }

    override val name: String
        get() = "Causal net"

    class CalcCNetJob : CalcJob<CausalNet>() {
        override fun mine(component: WorkspaceComponent, stream: DBHierarchicalXESInputStream): CausalNet {

            val miner = minerFromURN(component.algorithm)

            val model = miner.processLog(stream)
            return when (model) {
                is ProcessTree -> model.toCausalNet()
                is CausalNet -> model
                else -> throw IllegalStateException("Unexpected type of process model: ${model.javaClass.name}")
            }
        }

        override fun store(database: Database, model: CausalNet): String =
            DBSerializer.insert(database, model).toString()
    }

    class DeleteCNetJob : DeleteJob() {
        override fun delete(database: Database, id: String) = DBSerializer.delete(database, id.toInt())
    }
}
