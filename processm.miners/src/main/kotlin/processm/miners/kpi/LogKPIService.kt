package processm.miners.kpi

import jakarta.jms.MapMessage
import jakarta.jms.Message
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.transactions.transaction
import org.quartz.*
import processm.core.esb.AbstractJobService
import processm.core.esb.ServiceJob
import processm.core.helpers.toUUID
import processm.core.log.attribute.value
import processm.core.log.hierarchical.DBHierarchicalXESInputStream
import processm.core.logging.loggedScope
import processm.core.persistence.connection.DBCache
import processm.core.querylanguage.Query
import processm.dbmodels.models.*
import java.time.Instant
import java.util.*

/**
 * A service that calculates KPIs using PQL for [WorkspaceComponent]s.
 */
class LogKPIService : AbstractJobService(
    QUARTZ_CONFIG,
    WORKSPACE_COMPONENTS_TOPIC,
    "$WORKSPACE_COMPONENT_TYPE = '${ComponentTypeDto.Kpi}'"
) {
    companion object {
        private const val QUARTZ_CONFIG = "quartz-logkpi.properties"
    }

    override val name: String
        get() = "Log-based KPI"

    override fun loadJobs(): List<Pair<JobDetail, Trigger>> = loggedScope {
        val components = transaction(DBCache.getMainDBPool().database) {
            WorkspaceComponents.slice(WorkspaceComponents.id).select {
                WorkspaceComponents.componentType eq ComponentTypeDto.Kpi.toString() and WorkspaceComponents.data.isNull()
            }.map { it[WorkspaceComponents.id].value }
        }
        return components.map { createJob(it) }
    }

    override fun messageToJobs(message: Message): List<Pair<JobDetail, Trigger>> = loggedScope { logger ->
        require(message is MapMessage) { "Unrecognized message $message." }

        val type = ComponentTypeDto.byTypeNameInDatabase(message.getStringProperty(WORKSPACE_COMPONENT_TYPE))
        require(type == ComponentTypeDto.Kpi) { "Expected ${ComponentTypeDto.Kpi}, got $type." }

        val id = message.getString(WORKSPACE_COMPONENT_ID)
        val event = message.getString(WORKSPACE_COMPONENT_EVENT)

        return when (event) {
            CREATE_OR_UPDATE -> listOf(createJob(id.toUUID()!!))
            DELETE -> emptyList() /* ignore for now */
            else -> throw IllegalArgumentException("Unknown event type: $event.")
        }
    }

    private fun createJob(id: UUID): Pair<JobDetail, Trigger> = loggedScope {
        val job = JobBuilder
            .newJob(KPIJob::class.java)
            .withIdentity(id.toString())
            .build()
        val trigger = TriggerBuilder
            .newTrigger()
            .withIdentity(id.toString())
            .startNow()
            .build()

        return job to trigger
    }

    class KPIJob : ServiceJob {
        override fun execute(context: JobExecutionContext) = loggedScope { logger ->
            val id = requireNotNull(context.jobDetail.key.name?.toUUID())

            logger.debug("Calculating log-based KPI for component $id...")
            transaction(DBCache.getMainDBPool().database) {
                val component = WorkspaceComponent.findById(id)
                if (component === null) {
                    logger.error("Component with id $id is not found.")
                    return@transaction
                }

                try {
                    val stream =
                        DBHierarchicalXESInputStream(component.dataStoreId.toString(), Query(component.query), false)
                    val first = stream.take(2).toList()
                    require(first.size == 1) { "The query must return exactly one log." }
                    require(first[0].attributes.size == 1) { "The query must return exactly one attribute." }

                    component.data = first[0].attributes.values.first().value.toString()
                    component.dataLastModified = Instant.now()
                    component.lastError = null
                } catch (e: Exception) {
                    component.lastError = e.message
                    logger.warn("Cannot calculate log-based KPI for component with id $id.", e)
                }
            }
        }
    }
}
