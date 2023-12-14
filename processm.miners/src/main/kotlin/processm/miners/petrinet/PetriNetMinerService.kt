package processm.miners.petrinet

import org.jetbrains.exposed.sql.Database
import processm.core.log.hierarchical.DBHierarchicalXESInputStream
import processm.core.models.petrinet.DBSerializer
import processm.core.models.petrinet.PetriNet
import processm.core.models.petrinet.converters.toPetriNet
import processm.dbmodels.models.ComponentTypeDto
import processm.dbmodels.models.WorkspaceComponent
import processm.miners.AbstractMinerService
import processm.miners.CalcJob
import processm.miners.DeleteJob
import processm.miners.causalnet.onlineminer.OnlineMiner
import java.util.*

class PetriNetMinerService : AbstractMinerService(
    QUARTZ_CONFIG,
    ComponentTypeDto.PetriNet,
    CalcPetriNetJob::class.java,
    DeletePetriNetJob::class.java
) {
    companion object {
        private const val QUARTZ_CONFIG = "quartz-petrinet.properties"
    }

    override val name: String
        get() = "Petri net"

    class CalcPetriNetJob : CalcJob<PetriNet>() {
        override fun mine(component: WorkspaceComponent, stream: DBHierarchicalXESInputStream): PetriNet {
            val miner = OnlineMiner()
            for (log in stream) {
                miner.processLog(log)
            }
            return miner.result.toPetriNet()
        }

        override fun store(database: Database, model: PetriNet): String =
            DBSerializer.insert(database, model).toString()
    }

    class DeletePetriNetJob : DeleteJob() {
        override fun delete(database: Database, id: String) = DBSerializer.delete(database, UUID.fromString(id))
    }
}
