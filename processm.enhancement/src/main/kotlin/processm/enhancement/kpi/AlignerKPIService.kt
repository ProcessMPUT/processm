package processm.enhancement.kpi

import jakarta.jms.MapMessage
import jakarta.jms.Message
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.quartz.*
import processm.core.communication.Producer
import processm.core.esb.AbstractJobService
import processm.core.esb.ServiceJob
import processm.core.log.hierarchical.DBHierarchicalXESInputStream
import processm.core.models.commons.ProcessModel
import processm.core.persistence.DurablePersistenceProvider
import processm.core.persistence.connection.DBCache
import processm.core.persistence.connection.transactionMain
import processm.core.querylanguage.Query
import processm.dbmodels.afterCommit
import processm.dbmodels.models.*
import processm.helpers.toUUID
import processm.logging.loggedScope
import java.net.URI
import java.time.Instant
import java.util.*
import processm.core.models.causalnet.DBSerializer as CausalNetDBSerializer
import processm.core.models.petrinet.DBSerializer as PetriNetDBSerializer

class AlignerKPIService : AbstractJobService(
    QUARTZ_CONFIG,
    WORKSPACE_COMPONENTS_TOPIC,
    "$WORKSPACE_COMPONENT_EVENT = '$DATA_CHANGE' AND $WORKSPACE_COMPONENT_EVENT_DATA = '$DATA_CHANGE_MODEL'"
) {
    companion object {

        private const val QUARTZ_CONFIG = "quartz-alignerkpi.properties"

        private const val COMPONENT_ID = "componentId"

        private val producer = Producer()
    }

    override val name: String
        get() = "Aligner KPI"

    override fun loadJobs(): List<Pair<JobDetail, Trigger>> {
        return emptyList()
    }

    override fun messageToJobs(message: Message): List<Pair<JobDetail, Trigger>> {
        require(message is MapMessage) { "Unrecognized message $message." }

        val id = message.getString(WORKSPACE_COMPONENT_ID)
        val event = message.getStringProperty(WORKSPACE_COMPONENT_EVENT)
        val eventData = message.getStringProperty(WORKSPACE_COMPONENT_EVENT_DATA)

        require(event == DATA_CHANGE)
        require(eventData == DATA_CHANGE_MODEL)

        return listOf(createJob(id.toUUID()!!))
    }

    private fun createJob(id: UUID): Pair<JobDetail, Trigger> =
        loggedScope {
            val job = JobBuilder
                .newJob(AlignerKPIJob::class.java)
                .usingJobData(COMPONENT_ID, id.toString())
                .build()
            val trigger = TriggerBuilder
                .newTrigger()
                .startNow()
                .build()

            return job to trigger
        }


    class AlignerKPIJob : ServiceJob {
        override fun execute(context: JobExecutionContext?) = loggedScope { logger ->
            val ctx = requireNotNull(context)
            val componentId = requireNotNull((ctx.mergedJobDataMap[COMPONENT_ID] as String).toUUID())

            logger.info("Calculating alignment-based KPI for component $componentId...")
            transactionMain {
                val component = WorkspaceComponent.findById(componentId)
                if (component === null) {
                    logger.error("Component with id $id is not found.")
                    return@transactionMain
                }

                val data = checkNotNull(component.dataAsObject)

                try {
                    val model = getModel(component.componentType, component.dataStoreId, data[0].modelId)
                    val calculator = Calculator(model)
                    val log = DBHierarchicalXESInputStream(
                        component.dataStoreId.toString(), Query(component.query), false
                    )
                    val report = calculator.calculate(log)
                    val reportId = URI("urn:processm:alignmentkpireport:${UUID.randomUUID()}")
                    DurablePersistenceProvider(component.dataStoreId.toString()).use {
                        it.put(reportId, report)
                    }

                    data[0] = data[0].copy(alignmentKPIId = reportId.toString())
                    component.data = Json.encodeToString(data)
                    component.dataLastModified = Instant.now()
                    component.lastError = null

                    component.afterCommit {
                        this as WorkspaceComponent
                        triggerEvent(producer, event = DATA_CHANGE, eventData = DATA_CHANGE_ALIGNMENT_KPI)
                    }
                } catch (exception: Exception) {
                    logger.error("Error calculating alignment-based KPI for component $componentId", exception)
                    component.lastError = exception.message

                    component.afterCommit {
                        this as WorkspaceComponent
                        triggerEvent(producer, event = DATA_CHANGE, eventData = DATA_CHANGE_LAST_ERROR)
                    }
                }

            }
        }

        private fun getModel(
            modelType: ComponentTypeDto?,
            dataStoreId: UUID?,
            modelId: String
        ): ProcessModel = when (modelType) {
            ComponentTypeDto.CausalNet -> CausalNetDBSerializer.fetch(
                DBCache.get(dataStoreId.toString()).database,
                modelId.toInt()
            )

            ComponentTypeDto.PetriNet -> PetriNetDBSerializer.fetch(
                DBCache.get(dataStoreId.toString()).database,
                requireNotNull(modelId.toUUID())
            )

            else -> TODO("Retrieval of model type $modelType is not implemented.")
        }
    }
}
