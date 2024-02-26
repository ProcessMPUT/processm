package processm.enhancement.kpi

import jakarta.jms.MapMessage
import jakarta.jms.Message
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.select
import org.quartz.*
import processm.core.communication.Producer
import processm.core.esb.AbstractJobService
import processm.core.esb.ServiceJob
import processm.core.log.hierarchical.DBHierarchicalXESInputStream
import processm.core.models.causalnet.DBSerializer
import processm.core.models.commons.ProcessModel
import processm.core.persistence.connection.DBCache
import processm.core.persistence.connection.transactionMain
import processm.core.querylanguage.Query
import processm.dbmodels.models.*
import processm.helpers.toUUID
import processm.logging.loggedScope
import java.time.Instant
import java.util.*

class AlignerKPIService : AbstractJobService(
    QUARTZ_CONFIG,
    WORKSPACE_COMPONENTS_TOPIC,
    "$WORKSPACE_COMPONENT_TYPE = '${ComponentTypeDto.AlignerKpi}'"
) {
    companion object {
        private const val QUARTZ_CONFIG = "quartz-alignerkpi.properties"
    }

    override val name: String
        get() = "Aligner-based KPI"

    override fun loadJobs(): List<Pair<JobDetail, Trigger>> = loggedScope {
        val components = transactionMain {
            WorkspaceComponents.slice(WorkspaceComponents.id).select {
                WorkspaceComponents.componentType eq ComponentTypeDto.AlignerKpi.toString() and WorkspaceComponents.data.isNull()
            }.map { it[WorkspaceComponents.id].value }
        }
        return components.map { createJob(it) }
    }

    override fun messageToJobs(message: Message): List<Pair<JobDetail, Trigger>> = loggedScope {
        require(message is MapMessage) { "Unrecognized message $message." }

        val type = ComponentTypeDto.byTypeNameInDatabase(message.getStringProperty(WORKSPACE_COMPONENT_TYPE))
        require(type == ComponentTypeDto.AlignerKpi) { "Expected ${ComponentTypeDto.AlignerKpi}, got $type." }

        val id = message.getString(WORKSPACE_COMPONENT_ID)
        val event = message.getStringProperty(WORKSPACE_COMPONENT_EVENT)

        return when (event) {
            CREATE_OR_UPDATE -> listOf(createJob(id.toUUID()!!))
            DELETE -> emptyList() /* ignore for now */
            DATA_CHANGE -> emptyList() // ignore
            else -> throw IllegalArgumentException("Unknown event type: $event.")
        }
    }

    private fun createJob(id: UUID): Pair<JobDetail, Trigger> = loggedScope {
        val job = JobBuilder
            .newJob(AlignerKPIJob::class.java)
            .withIdentity(id.toString())
            .build()
        val trigger = TriggerBuilder
            .newTrigger()
            .withIdentity(id.toString())
            .startNow()
            .build()

        return job to trigger
    }

    class AlignerKPIJob : ServiceJob {
        override fun execute(context: JobExecutionContext): Unit = loggedScope { logger ->
            val id = requireNotNull(context.jobDetail.key.name?.toUUID())

            logger.debug("Calculating aligner-based KPI for component $id...")
            transactionMain {
                val component = WorkspaceComponent.findById(id)
                if (component === null) {
                    logger.error("Component with id $id is not found.")
                    return@transactionMain
                }

                try {
                    val model: ProcessModel = component.getModel()
                    val calculator = Calculator(model)
                    val log = DBHierarchicalXESInputStream(
                        component.dataStoreId.toString(),
                        Query(component.query),
                        false
                    )
                    val report = calculator.calculate(log)


                    component.data = report.toJson()
                    component.dataLastModified = Instant.now()
                    component.lastError = null
                } catch (e: Exception) {
                    component.lastError = e.message
                    logger.warn("Cannot calculate aligner-based KPI for component with id $id.", e)
                }
                component.triggerEvent(Producer(), DATA_CHANGE)
            }
        }

        private fun WorkspaceComponent.getModel(): ProcessModel = when (modelType) {
            null -> throw IllegalArgumentException("Model type is not set for component $id")
            ModelTypeDto.CausalNet -> DBSerializer.fetch(
                DBCache.get(dataStoreId.toString()).database,
                modelId!!.toInt()
            )

            else -> TODO("Retrieval of model type $modelType is not implemented.")
        }
    }
}
