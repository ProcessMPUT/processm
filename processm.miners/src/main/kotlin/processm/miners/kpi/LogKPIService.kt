package processm.miners.kpi

import jakarta.jms.MapMessage
import jakarta.jms.Message
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.select
import org.quartz.*
import processm.core.esb.AbstractJobService
import processm.core.esb.ServiceJob
import processm.core.log.hierarchical.DBHierarchicalXESInputStream
import processm.core.persistence.connection.transactionMain
import processm.core.querylanguage.Query
import processm.dbmodels.afterCommit
import processm.dbmodels.models.*
import processm.helpers.AbstractLocalizedException
import processm.helpers.toUUID
import processm.logging.loggedScope
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
        val components = transactionMain {
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
        val event = WorkspaceComponentEventType.valueOf(message.getStringProperty(WORKSPACE_COMPONENT_EVENT))

        return when (event) {
            WorkspaceComponentEventType.ComponentCreatedOrUpdated, WorkspaceComponentEventType.NewExternalData -> listOf(
                createJob(id.toUUID()!!)
            )

            else -> emptyList()
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
        override fun execute(context: JobExecutionContext): Unit = loggedScope { logger ->
            val id = requireNotNull(context.jobDetail.key.name?.toUUID())

            logger.debug("Calculating log-based KPI for component $id...")
            transactionMain {
                val component = WorkspaceComponent.findById(id)
                if (component === null) {
                    logger.error("Component with id $id is not found.")
                    return@transactionMain
                }

                try {
                    // since no computation beyond the query itself is involved, we don't use versioning here, as it'd only increase the complexity instead of decreasing it
                    val stream =
                        DBHierarchicalXESInputStream(component.dataStoreId.toString(), Query(component.query), false)
                    val first = stream.take(2).toList()
                    require(first.size == 1) { "The query returned ${first.size} event logs but exactly one event log is expected." }
                    require(first[0].attributes.size == 1) { "The query returned ${first[0].attributes.size} attributes but exactly one attribute is expected." }

                    component.data = first[0].attributes.values.first().toString()
                    component.dataLastModified = Instant.now()
                    component.lastError = null

                    component.afterCommit {
                        // TODO: add support for versioning of KPI values; for now just send it as initial model to just update value in GUI
                        component.triggerEvent(eventData = DataChangeType.InitialModel)
                    }
                } catch (e: Exception) {
                    // FIXME: drop hardcoded locale in favor of storing error id or serialized exception and formatting
                    //  error messages in processm.services for the given request locale
                    component.lastError =
                        if (e is AbstractLocalizedException) e.localizedMessage(Locale.ENGLISH) else e.message

                    component.afterCommit {
                        component.triggerEvent(eventData = DataChangeType.LastError)
                    }

                    logger.warn("Cannot calculate log-based KPI for component with id $id.", e)
                }
            }
        }
    }
}
