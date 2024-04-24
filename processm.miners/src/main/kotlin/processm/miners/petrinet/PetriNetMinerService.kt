package processm.miners.petrinet

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.sql.Database
import processm.core.log.hierarchical.DBHierarchicalXESInputStream
import processm.core.models.causalnet.CausalNet
import processm.core.models.petrinet.DBSerializer
import processm.core.models.petrinet.PetriNet
import processm.core.models.petrinet.computeLayout
import processm.core.models.petrinet.converters.toPetriNet
import processm.core.models.processtree.ProcessTree
import processm.dbmodels.models.ComponentTypeDto
import processm.dbmodels.models.WorkspaceComponent
import processm.logging.logger
import processm.miners.AbstractMinerService
import processm.miners.CalcJob
import processm.miners.DeleteJob
import processm.miners.MinerJob
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

    interface PetriNetJob : MinerJob<PetriNet> {
        override fun mine(component: WorkspaceComponent, stream: DBHierarchicalXESInputStream): PetriNet {
            val miner = minerFromProperties(component.properties)

            val model = miner.processLog(stream)
            return when (model) {
                is ProcessTree -> model.toPetriNet()
                is CausalNet -> model.toPetriNet()
                else -> throw IllegalStateException("Unexpected type of process model: ${model.javaClass.name}")
            }
        }

        override fun delete(database: Database, id: String) =
            DBSerializer.delete(database, UUID.fromString(id))

        override fun store(database: Database, model: PetriNet): String =
            DBSerializer.insert(database, model).toString()

        override fun updateCustomizationData(model: PetriNet, customizationData: String?): String? {
            return try {
                Json.encodeToString(model.computeLayout())
            } catch (e: Throwable) {
                logger().warn("Failed to compute layout", e)
                null
            }
        }
    }

    class CalcPetriNetJob : CalcJob<PetriNet>(), PetriNetJob

    class DeletePetriNetJob : DeleteJob<PetriNet>(), PetriNetJob
}
