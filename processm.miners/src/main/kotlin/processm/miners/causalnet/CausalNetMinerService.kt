package processm.miners.causalnet

import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.transactions.transaction
import org.quartz.*
import processm.core.esb.AbstractJobService
import processm.core.esb.ServiceJob
import processm.core.helpers.toUUID
import processm.core.log.hierarchical.DBHierarchicalXESInputStream
import processm.core.logging.loggedScope
import processm.core.models.causalnet.DBSerializer
import processm.core.persistence.connection.DBCache
import processm.core.querylanguage.Query
import processm.dbmodels.models.*
import processm.miners.causalnet.onlineminer.OnlineMiner
import java.time.Instant
import java.util.*
import javax.jms.MapMessage
import javax.jms.Message

class CausalNetMinerService : AbstractJobService(
    QUARTZ_CONFIG,
    WORKSPACE_COMPONENTS_TOPIC,
    "$WORKSPACE_COMPONENT_TYPE = '${ComponentTypeDto.CausalNet}'"
) {
    companion object {
        private const val QUARTZ_CONFIG = "quartz-causalnet.properties"
    }

    override val name: String
        get() = "Causal net"

    override fun loadJobs(): List<Pair<JobDetail, Trigger>> = loggedScope {
        val components = transaction(DBCache.getMainDBPool().database) {
            WorkspaceComponents.slice(WorkspaceComponents.id).select {
                WorkspaceComponents.componentType eq ComponentTypeDto.CausalNet.toString() and WorkspaceComponents.data.isNull()
            }.map { it[WorkspaceComponents.id].value }
        }
        return components.map { createJob(it, CalcCNetJob::class.java) }
    }

    override fun messageToJobs(message: Message): List<Pair<JobDetail, Trigger>> {
        require(message is MapMessage) { "Unrecognized message $message." }

        val type = ComponentTypeDto.byTypeNameInDatabase(message.getStringProperty(WORKSPACE_COMPONENT_TYPE))
        require(type == ComponentTypeDto.CausalNet) { "Expected ${ComponentTypeDto.CausalNet}, got $type." }

        val id = message.getString(WORKSPACE_COMPONENT_ID)
        val event = message.getString(WORKSPACE_COMPONENT_EVENT)

        return when (event) {
            CREATE_OR_UPDATE -> listOf(createJob(id.toUUID()!!, CalcCNetJob::class.java))
            DELETE -> listOf(createJob(id.toUUID()!!, DeleteCNetJob::class.java))
            else -> throw IllegalArgumentException("Unknown event type: $event.")
        }
    }

    private fun createJob(id: UUID, klass: java.lang.Class<out Job>): Pair<JobDetail, Trigger> = loggedScope {
        val job = JobBuilder
            .newJob(klass)
            .withIdentity(id.toString())
            .build()
        val trigger = TriggerBuilder
            .newTrigger()
            .withIdentity(id.toString())
            .startNow()
            .build()

        return job to trigger
    }

    class CalcCNetJob : ServiceJob {
        override fun execute(context: JobExecutionContext) = loggedScope { logger ->
            val id = requireNotNull(context.jobDetail.key.name?.toUUID())

            logger.debug("Calculating Causal net for component $id...")
            transaction(DBCache.getMainDBPool().database) {
                val component = WorkspaceComponent.findById(id)
                if (component === null) {
                    logger.error("Component with id $id is not found.")
                    return@transaction
                }

                // TODO: store the entire history of models in the component.data field

                try {
                    val stream = DBHierarchicalXESInputStream(
                        component.dataStoreId.toString(),
                        Query(component.query),
                        false
                    )

                    val miner = OnlineMiner()
                    for (log in stream) {
                        miner.processLog(log)
                    }

                    val cnetId = DBSerializer.insert(
                        DBCache.get(component.dataStoreId.toString()).database,
                        miner.result
                    )

                    component.data = cnetId.toString()
                    component.dataLastModified = Instant.now()
                    component.lastError = null
                } catch (e: Exception) {
                    component.lastError = e.message
                    logger.warn("Cannot calculate Causal net for component with id $id.", e)
                }
            }
        }
    }

    class DeleteCNetJob : ServiceJob {
        override fun execute(context: JobExecutionContext) = loggedScope { logger ->
            val id = requireNotNull(context.jobDetail.key.name?.toUUID())

            logger.debug("Deleting Causal net for component $id...")

            transaction(DBCache.getMainDBPool().database) {
                val component = WorkspaceComponent.findById(id)
                if (component === null) {
                    logger.error("Component with id $id is not found.")
                    return@transaction
                }

                try {

                    val cnetId = component.data?.let(String::toInt)
                        ?: throw NoSuchElementException("C-net does not exist for component $id.")
                    DBSerializer.delete(DBCache.get(component.dataStoreId.toString()).database, cnetId)

                } catch (e: Exception) {
                    component.lastError = e.message
                    logger.warn("Error deleting C-Net for component with id $id.")
                }
            }
        }

    }
}
