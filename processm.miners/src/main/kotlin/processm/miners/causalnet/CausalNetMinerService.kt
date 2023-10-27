package processm.miners.causalnet

import org.jetbrains.exposed.sql.Database
import processm.core.log.hierarchical.DBHierarchicalXESInputStream
import processm.core.models.causalnet.CausalNet
import processm.core.models.causalnet.DBSerializer
import processm.dbmodels.models.ComponentTypeDto
import processm.miners.AbstractMinerService
import processm.miners.CalcJob
import processm.miners.DeleteJob
import processm.miners.causalnet.onlineminer.OnlineMiner

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
        override fun mine(stream: DBHierarchicalXESInputStream): CausalNet {
            val miner = OnlineMiner()
            for (log in stream) {
                miner.processLog(log)
            }
            return miner.result
        }

        override fun store(database: Database, model: CausalNet): String =
            DBSerializer.insert(database, model).toString()
    }

    class DeleteCNetJob : DeleteJob() {
        override fun delete(database: Database, id: String) = DBSerializer.delete(database, id.toInt())
    }
}
