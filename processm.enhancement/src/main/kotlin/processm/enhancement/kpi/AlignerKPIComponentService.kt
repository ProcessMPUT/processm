package processm.enhancement.kpi

import jakarta.jms.MapMessage
import jakarta.jms.Message
import org.jetbrains.exposed.sql.and
import processm.core.communication.Producer
import processm.core.esb.AbstractJMSListener
import processm.core.esb.Artemis
import processm.core.esb.Service
import processm.core.esb.ServiceStatus
import processm.core.persistence.connection.transactionMain
import processm.dbmodels.afterCommit
import processm.dbmodels.models.*
import processm.helpers.toUUID
import processm.logging.loggedScope
import java.time.Instant
import java.util.*
import kotlin.reflect.KClass

class AlignerKPIComponentService : Service {
    companion object {
        private val producer = Producer()
    }

    override val name: String
        get() = "Aligner KPI component"

    override val dependencies: List<KClass<out Service>>
        get() = listOf(Artemis::class, AlignerKPIService::class)

    override var status: ServiceStatus = ServiceStatus.Unknown
        private set

    /**
     * Listens for new and updated [WorkspaceComponent]s of AlignerKPI type.
     */
    private val workspaceComponentListener = object : AbstractJMSListener(
        topicName = WORKSPACE_COMPONENTS_TOPIC,
        filter = "$WORKSPACE_COMPONENT_TYPE = '${ComponentTypeDto.AlignerKpi}'",
        name = "$name workspace component queue"
    ) {
        override fun onMessage(message: Message?) {
            require(message is MapMessage) { "Unrecognized message $message." }

            val type = ComponentTypeDto.byTypeNameInDatabase(message.getStringProperty(WORKSPACE_COMPONENT_TYPE))
            require(type == ComponentTypeDto.AlignerKpi) { "Expected ${ComponentTypeDto.AlignerKpi}, got $type." }

            val id = message.getString(WORKSPACE_COMPONENT_ID)
            val event = message.getStringProperty(WORKSPACE_COMPONENT_EVENT)

            when (event) {
                CREATE_OR_UPDATE -> execute(id.toUUID()!!)
                DELETE -> Unit /* ignore for now */
                DATA_CHANGE -> Unit // ignore
                else -> throw IllegalArgumentException("Unknown event type: $event.")
            }
        }

        fun execute(id: UUID) = loggedScope { logger ->
            logger.debug("Calculating alignment-based KPI for component $id...")
            transactionMain {
                val component = WorkspaceComponent.findById(id)
                if (component === null) {
                    logger.error("Component with id $id is not found.")
                    return@transactionMain
                }

                producer.produce(AlignerKPIService.TOPIC) {
                    setStringProperty(AlignerKPIService.TYPE, AlignerKPIService.TYPE_REQUEST)
                    setString(AlignerKPIService.DATASTORE_ID, component.dataStoreId.toString())
                    setString(AlignerKPIService.MODEL_TYPE, component.modelType.toString())
                    setString(AlignerKPIService.MODEL_ID, component.modelId.toString())
                    setString(AlignerKPIService.QUERY, component.query)
                }
            }
        }

    }

    /**
     * Listens for alignment-based KPI reports.
     */
    private val alignerKPIReportListener = object : AbstractJMSListener(
        topicName = AlignerKPIService.TOPIC,
        filter = "${AlignerKPIService.TYPE} = '${AlignerKPIService.TYPE_REPORT}'",
        name = "$name report queue"
    ) {
        override fun onMessage(message: Message?) = loggedScope { logger ->
            require(message is MapMessage) { "Unrecognized message $message." }

            try {
                val type = message.getStringProperty(AlignerKPIService.TYPE)
                require(type == AlignerKPIService.TYPE_REPORT) { "Unexpected message type: $type; expecting ${AlignerKPIService.TYPE_REPORT}." }

                val datastoreId = UUID.fromString(message.getString(AlignerKPIService.DATASTORE_ID))
                val modelTypeStr = message.getString(AlignerKPIService.MODEL_TYPE)
                val modelType = ModelTypeDto.valueOf(modelTypeStr)
                val modelId = message.getString(AlignerKPIService.MODEL_ID)
                val query = message.getString(AlignerKPIService.QUERY)
                val report = message.getString(AlignerKPIService.REPORT)?.let { Report.fromJson(it) }
                val error = message.getString(AlignerKPIService.ERROR)

                transactionMain {
                    WorkspaceComponent.find {
                        (WorkspaceComponents.dataStoreId eq datastoreId) and
                                (WorkspaceComponents.modelType eq modelType.typeName) and
                                (WorkspaceComponents.modelId eq modelId.toLong()) and
                                (WorkspaceComponents.query eq query)

                    }.forEach { component ->
                        // update data for each matching component
                        logger.debug("Saving alignment-based KPI report for component ${component.id}")

                        component.data = report?.toJson()
                        component.lastError = error
                        if (component.data !== null)
                            component.dataLastModified = Instant.now()

                        component.afterCommit {
                            this as WorkspaceComponent
                            triggerEvent(producer, event = DATA_CHANGE)
                        }
                    }
                }
            } catch (e: Exception) {
                logger.error("Error saving alignment-based KPI report", e)
            }
        }

    }

    override fun register() = loggedScope {
        status = ServiceStatus.Stopped
    }

    override fun start() = loggedScope {
        workspaceComponentListener.listen()
        alignerKPIReportListener.listen()
        /*
                transactionMain {
                    WorkspaceComponents.slice(WorkspaceComponents.id).select {
                        WorkspaceComponents.componentType eq ComponentTypeDto.AlignerKpi.toString() and WorkspaceComponents.data.isNull()
                    }.map { it[WorkspaceComponents.id].value }
                }.forEach {
                    producer.produce(WORKSPACE_COMPONENTS_TOPIC) {
                        setStringProperty(WORKSPACE_COMPONENT_TYPE, ComponentTypeDto.AlignerKpi.toString())
                        setString(WORKSPACE_COMPONENT_ID, it)
                        setString(WORKSPACE_COMPONENT_EVENT, CREATE_OR_UPDATE)
                    }
                }
        */
        status = ServiceStatus.Started
    }

    override fun stop() = loggedScope {
        alignerKPIReportListener.close()
        workspaceComponentListener.close()
        status = ServiceStatus.Stopped
    }

}
