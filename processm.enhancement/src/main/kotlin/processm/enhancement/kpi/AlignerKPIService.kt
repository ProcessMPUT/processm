package processm.enhancement.kpi

import jakarta.jms.MapMessage
import jakarta.jms.Message
import org.quartz.*
import processm.conformance.conceptdrift.BoundStatisticalDistanceDriftDetector
import processm.conformance.conceptdrift.DriftDetector
import processm.conformance.conceptdrift.statisticaldistance.NaiveJensenShannonDivergence
import processm.conformance.models.alignments.Alignment
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
import processm.helpers.getPropertyIgnoreCase
import processm.helpers.toUUID
import processm.logging.loggedScope
import java.net.URI
import java.time.Instant
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import processm.core.models.causalnet.DBSerializer as CausalNetDBSerializer
import processm.core.models.petrinet.DBSerializer as PetriNetDBSerializer

class AlignerKPIService : AbstractJobService(
    QUARTZ_CONFIG,
    WORKSPACE_COMPONENTS_TOPIC,
    "$WORKSPACE_COMPONENT_EVENT IN ('${WorkspaceComponentEventType.ModelAccepted}', '${WorkspaceComponentEventType.Delete}', '${WorkspaceComponentEventType.NewExternalData}')",
) {
    companion object {

        private const val QUARTZ_CONFIG = "quartz-alignerkpi.properties"

        private const val COMPONENT_ID = "componentId"

        const val QUEUE_DELAY_MS_PROPERTY = "processm.enhancement.kpi.alignerkpi.queueDelayMs"

        private val producer = Producer()

        /**
         * Maps a componentID into a pair consisting of the model version and the drift detector for that model
         */
        private val driftDetectors = ConcurrentHashMap<UUID, Pair<Long, DriftDetector<Alignment, List<Alignment>>>>()
    }

    private val queueDelayMs: Long
        get() = getPropertyIgnoreCase(QUEUE_DELAY_MS_PROPERTY)?.toLongOrNull()
            ?: 5_000

    override val name: String
        get() = "Aligner KPI"

    override fun loadJobs(): List<Pair<JobDetail, Trigger>> {
        return emptyList()
    }

    override fun messageToJobs(message: Message): List<Pair<JobDetail, Trigger>> {
        require(message is MapMessage) { "Unrecognized message $message." }

        val id = message.getString(WORKSPACE_COMPONENT_ID)
        val event = WorkspaceComponentEventType.valueOf(message.getStringProperty(WORKSPACE_COMPONENT_EVENT))

        when (event) {
            WorkspaceComponentEventType.ModelAccepted, WorkspaceComponentEventType.NewExternalData -> {
                return listOf(createComputeJob(id.toUUID()!!))
            }

            WorkspaceComponentEventType.Delete -> {
                return listOf(createDeleteJob(id.toUUID()!!))
            }

            else -> throw IllegalArgumentException("Unrecognized event: $event")
        }
    }

    private fun createComputeJob(id: UUID): Pair<JobDetail, Trigger> = loggedScope { logger ->
        val job = JobBuilder
            .newJob(AlignerKPIJob::class.java)
            .usingJobData(COMPONENT_ID, id.toString())
            .withIdentity(id.toString(), "AlignerKPIService/ComputeJob")
            .build()
        val trigger = TriggerBuilder
            .newTrigger()
            .withIdentity(id.toString(), "AlignerKPIService/ComputeJob")
            .startAt(Date.from(Instant.now().plusMillis(queueDelayMs)))
            .build()

        return job to trigger
    }

    private fun createDeleteJob(id: UUID): Pair<JobDetail, Trigger> = loggedScope {
        val job = JobBuilder
            .newJob(DeleteJob::class.java)
            .usingJobData(COMPONENT_ID, id.toString())
            .build()
        val trigger = TriggerBuilder
            .newTrigger()
            .startNow()
            .build()

        return job to trigger
    }

    class AlignerKPIJob : ServiceJob {

        private fun createDetector(data: ProcessModelComponentData, modelVersion: Long) =
            retrieveAlignments(data, modelVersion, modelVersion)?.let { alignments ->
                BoundStatisticalDistanceDriftDetector(::NaiveJensenShannonDivergence).apply {
                    fit(alignments)
                }
            }

        private fun retrieveAlignments(
            data: ProcessModelComponentData,
            modelVersion: Long,
            dataVersion: Long
        ): List<Alignment>? {
            val reportId = data.getAlignmentKPIReport(modelVersion, dataVersion) ?: return null
            return DurablePersistenceProvider(data.component.dataStoreId.toString()).use {
                return@use it.get<Report>(reportId, Report::class).alignments
            }
        }

        override fun execute(context: JobExecutionContext?) = loggedScope { logger ->
            val ctx = requireNotNull(context)
            val componentId = requireNotNull((ctx.mergedJobDataMap[COMPONENT_ID] as String).toUUID())

            logger.debug("Calculating alignment-based KPI for component $componentId...")
            transactionMain {
                val component = WorkspaceComponent.findById(componentId)
                if (component === null) {
                    logger.error("Component with id $id is not found.")
                    return@transactionMain
                }
                if (component.deleted) {
                    logger.debug("Component with id $id is deleted, ignoring")
                    return@transactionMain
                }

                try {
                    val componentData = ProcessModelComponentData.create(component)
                    val acceptedModelVersion = componentData.acceptedModelVersion
                    val acceptedModelId = componentData.acceptedModelId
                    logger.debug("AlignerKPIJob for $componentId acceptedModelVersion=$acceptedModelVersion")
                    if (acceptedModelVersion === null || acceptedModelId === null) {
                        component.afterCommit {
                            this as WorkspaceComponent
                            triggerEvent(producer, event = WorkspaceComponentEventType.NewModelRequired)
                        }
                        return@transactionMain
                    }

                    val model = getModel(component.componentType, component.dataStoreId, acceptedModelId)
                    val calculator = Calculator(model)
                    val log = DBHierarchicalXESInputStream(
                        component.dataStoreId.toString(), Query(component.query), false
                    )
                    val dataVersion = log.readVersion()
                    val report = calculator.calculate(log)
                    if (acceptedModelVersion < dataVersion) {
                        driftDetectors.compute(componentId) { _, oldValue ->
                            if (oldValue?.first != acceptedModelVersion)
                                createDetector(componentData, acceptedModelVersion)?.let { acceptedModelVersion to it }
                            else
                                oldValue
                        }?.second?.let { detector ->
                            report.alignments.forEach(detector::observe)
                            report.hasConceptDrift = detector.drift
                        }
                    }
                    logger.debug("Concept drift status: {}", report.hasConceptDrift)
                    val reportId = URI("urn:processm:alignmentkpireport:${UUID.randomUUID()}")
                    DurablePersistenceProvider(component.dataStoreId.toString()).use {
                        it.put(reportId, report)
                    }
                    componentData.addAlignmentKPIReport(acceptedModelVersion, dataVersion, reportId)

                    component.data = componentData.toJSON()
                    component.dataLastModified = Instant.now()
                    component.lastError = null

                    component.afterCommit {
                        this as WorkspaceComponent
                        triggerEvent(producer, DataChangeType.AlignmentKPI)
                        if (report.hasConceptDrift == true)
                            this.triggerEvent(event = WorkspaceComponentEventType.NewModelRequired)
                    }
                } catch (exception: Exception) {
                    logger.error("Error calculating alignment-based KPI for component $componentId", exception)
                    component.lastError = exception.message

                    component.afterCommit {
                        this as WorkspaceComponent
                        triggerEvent(producer, DataChangeType.LastError)
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

    class DeleteJob : ServiceJob {
        override fun execute(context: JobExecutionContext?) = loggedScope { logger ->
            val ctx = requireNotNull(context)
            val componentId = requireNotNull((ctx.mergedJobDataMap[COMPONENT_ID] as String).toUUID())

            transactionMain {
                val component = WorkspaceComponent.findById(componentId)
                if (component === null) {
                    logger.error("Component with id $id is not found.")
                    return@transactionMain
                }

                val dataObject = ProcessModelComponentData.create(component)
                val reportIds = dataObject.alignmentKPIReports.flatMap { it.value.values }

                try {
                    DurablePersistenceProvider(component.dataStoreId.toString()).use {
                        reportIds.forEach(it::delete)
                    }
                } catch (exception: Exception) {
                    logger.error("Error deleting alignment-based KPI for component $componentId", exception)
                    component.lastError = exception.message

                    component.afterCommit {
                        this as WorkspaceComponent
                        triggerEvent(producer, DataChangeType.LastError)
                    }
                }

            }
        }
    }
}
