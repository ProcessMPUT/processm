package processm.miners.processtree.directlyfollowsgraph

import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.transactions.transaction
import processm.core.log.hierarchical.DBHierarchicalXESInputStream
import processm.core.models.dfg.DirectlyFollowsGraph
import processm.dbmodels.models.ComponentTypeDto
import processm.dbmodels.models.DFG
import processm.dbmodels.models.store
import processm.miners.AbstractMinerService
import processm.miners.CalcJob
import processm.miners.DeleteJob
import java.util.*

/**
 * A miner service that crates a directly-follows graph given event log.
 */
class DirectlyFollowsGraphMinerService : AbstractMinerService(
    QUARTZ_CONFIG,
    ComponentTypeDto.DirectlyFollowsGraph,
    CalcDFGJob::class.java,
    DeleteDFGJob::class.java
) {
    companion object {
        private const val QUARTZ_CONFIG = "quartz-dfg.properties"
    }

    override val name: String
        get() = "Directly-follows graph"

    class CalcDFGJob : CalcJob<DirectlyFollowsGraph>() {
        override fun mine(stream: DBHierarchicalXESInputStream): DirectlyFollowsGraph {
            val dfg = DirectlyFollowsGraph()
            dfg.discover(stream)
            return dfg;
        }

        override fun store(database: Database, model: DirectlyFollowsGraph): String =
            model.store(database).toString()
    }

    class DeleteDFGJob : DeleteJob() {
        override fun delete(database: Database, id: String): Unit = transaction(database) {
            DFG[UUID.fromString(id)].delete()
        }

    }
}
