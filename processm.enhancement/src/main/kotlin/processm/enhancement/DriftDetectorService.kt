package processm.enhancement

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
import processm.core.persistence.DurablePersistenceProvider
import processm.core.persistence.connection.transactionMain
import processm.dbmodels.afterCommit
import processm.dbmodels.models.*
import processm.enhancement.kpi.Report
import processm.helpers.toUUID
import processm.logging.loggedScope
import java.util.*
import java.util.concurrent.ConcurrentHashMap


class DriftDetectorService : AbstractJobService(
    QUARTZ_CONFIG,
    WORKSPACE_COMPONENTS_TOPIC,
    "$WORKSPACE_COMPONENT_EVENT = '${WorkspaceComponentEventType.NewAlignments}'",
) {
    companion object {

        private const val QUARTZ_CONFIG = "quartz-driftdetector.properties"

        private const val COMPONENT_ID = "componentId"

        private val driftDetectors = ConcurrentHashMap<UUID, Pair<Long, DriftDetector<Alignment, List<Alignment>>>>()

        private val producer = Producer()
    }

    override val name: String
        get() = "Drift Detector"

    override fun loadJobs(): List<Pair<JobDetail, Trigger>> {
        return emptyList()
    }

    override fun messageToJobs(message: Message): List<Pair<JobDetail, Trigger>> {
        require(message is MapMessage) { "Unrecognized message $message." }

        val id = message.getString(WORKSPACE_COMPONENT_ID)
        val event = WorkspaceComponentEventType.valueOf(message.getStringProperty(WORKSPACE_COMPONENT_EVENT))
        val modelVersion = message.getLong(MODEL_VERSION)
        val dataVersion = message.getLong(DATA_VERSION)

        when (event) {
            WorkspaceComponentEventType.NewAlignments -> {
                return listOf(createComputeJob(id.toUUID()!!, modelVersion, dataVersion))
            }

            else -> throw IllegalArgumentException("Unrecognized event: $event")
        }
    }

    private fun createComputeJob(id: UUID, modelVersion: Long, dataVersion: Long): Pair<JobDetail, Trigger> =
        loggedScope {
            val job = JobBuilder
                .newJob(DriftDetectorJob::class.java)
                .usingJobData(COMPONENT_ID, id.toString())
                .usingJobData(MODEL_VERSION, modelVersion)
                .usingJobData(DATA_VERSION, dataVersion)
                .withIdentity(id.toString(), "DriftDetectorService/ComputeJob")
                .build()
            val trigger = TriggerBuilder
                .newTrigger()
                .withIdentity(id.toString(), "DriftDetectorService/ComputeJob")
                .startNow()
                .build()

            return job to trigger
        }

    class DriftDetectorJob : ServiceJob {
        private fun createDetector(data: ProcessModelComponentData, modelVersion: Long) =
            BoundStatisticalDistanceDriftDetector(::NaiveJensenShannonDivergence).apply {
                fit(checkNotNull(retrieveAlignments(data, modelVersion, modelVersion)))
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
            val modelVersion = requireNotNull((ctx.mergedJobDataMap[MODEL_VERSION] as Long))
            val dataVersion = requireNotNull((ctx.mergedJobDataMap[DATA_VERSION] as Long))
            if (modelVersion == dataVersion) {
                logger.debug("Not checking for concept drift since it cannot be detected if modelVersion = dataVersion")
                return@loggedScope
            }
            transactionMain {
                val component = WorkspaceComponent.findById(componentId)
                if (component === null) {
                    logger.error("Component with id $id is not found.")
                    return@transactionMain
                }
                try {
                    val data = ProcessModelComponentData(component)
                    logger.debug("Detecting drift for {} @ ({}, {})", componentId, modelVersion, dataVersion)
                    val detector = driftDetectors.compute(componentId) { _, oldValue ->
                        if (oldValue?.first != modelVersion) modelVersion to createDetector(data, modelVersion)
                        else oldValue
                    }!!.second
                    checkNotNull(retrieveAlignments(data, modelVersion, dataVersion)).forEach(detector::observe)
                    logger.debug("Drift status for {}: {}", componentId, detector.drift)
                    if (data.hasConceptDrift != detector.drift) {
                        data.hasConceptDrift = detector.drift
                        component.data = data.toJSON()
                        component.afterCommit {
                            this as WorkspaceComponent
                            this.triggerEvent(
                                event = WorkspaceComponentEventType.DataChange,
                                eventData = DATA_CHANGE_CONCEPT_DRIFT
                            )
                        }
                    }
                    if (detector.drift)
                        component.afterCommit {
                            this as WorkspaceComponent
                            this.triggerEvent(
                                event = WorkspaceComponentEventType.ConceptDriftDetected
                            ) {
                                setLong(MODEL_VERSION, modelVersion)
                                setLong(DATA_VERSION, dataVersion)
                            }
                        }
                } catch (exception: Exception) {
                    logger.error("Error detecting drift for component $componentId", exception)
                    component.lastError = exception.message

                    component.afterCommit {
                        this as WorkspaceComponent
                        triggerEvent(
                            producer,
                            event = WorkspaceComponentEventType.DataChange,
                            eventData = DATA_CHANGE_LAST_ERROR
                        )
                    }
                }
            }
        }
    }
}
