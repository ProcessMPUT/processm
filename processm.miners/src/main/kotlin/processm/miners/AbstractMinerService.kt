package processm.miners

import jakarta.jms.MapMessage
import jakarta.jms.Message
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.select
import org.quartz.*
import processm.core.communication.Producer
import processm.core.esb.AbstractJobService
import processm.core.esb.ServiceJob
import processm.core.log.hierarchical.DBHierarchicalXESInputStream
import processm.core.models.commons.ProcessModel
import processm.core.persistence.connection.DBCache
import processm.core.persistence.connection.transactionMain
import processm.core.querylanguage.Query
import processm.dbmodels.afterCommit
import processm.dbmodels.models.*
import processm.helpers.toUUID
import processm.logging.loggedScope
import processm.miners.causalnet.onlineminer.OnlineMiner
import processm.miners.causalnet.onlineminer.replayer.SingleReplayer
import processm.miners.processtree.inductiveminer.OnlineInductiveMiner
import java.time.Instant
import java.util.*

const val ALGORITHM_HEURISTIC_MINER = "urn:processm:miners/OnlineHeuristicMiner"
const val ALGORITHM_INDUCTIVE_MINER = "urn:processm:miners/OnlineInductiveMiner"

interface MinerJob<T : ProcessModel> : ServiceJob {
    fun minerFromProperties(properties: Map<String, String>): Miner = when (properties["algorithm"]) {
        ALGORITHM_INDUCTIVE_MINER -> OnlineInductiveMiner()
        ALGORITHM_HEURISTIC_MINER, null -> OnlineMiner(
            SingleReplayer(
                horizon = properties["horizon"]?.toIntOrNull()?.let { if (it > 0) it else null })
        )

        else -> throw IllegalArgumentException("Unexpected type of miner: ${properties["algorithm"]}.")
    }

    fun mine(component: WorkspaceComponent, stream: DBHierarchicalXESInputStream): T

    /**
     * Given the newly-mined [model] and the previous content of [WorkspaceComponents.customizationData] (in
     * [customizationData]) return the new value for the field.
     *
     * The default implementation returns [customizationData] without any changes
     */
    fun updateCustomizationData(model: T, customizationData: String?): String? = customizationData
    fun store(database: Database, model: T): String

    /**
     * @param id A value returned by [store]
     */
    fun delete(database: Database, id: String)

    fun batchDelete(database: Database, component: WorkspaceComponent) {
        ProcessModelComponentData(component).models.values.forEach { delete(database, it) }
    }
}

abstract class CalcJob<T : ProcessModel> : MinerJob<T> {
    companion object {
        private val producer = Producer()
    }

    override fun execute(context: JobExecutionContext): Unit = loggedScope { logger ->
        val id = requireNotNull(context.jobDetail.key.name?.toUUID())

        logger.debug("Calculating model for component $id...")
        transactionMain {
            val component = WorkspaceComponent.findById(id)
            if (component === null) {
                logger.error("Component with id $id is not found.")
                return@transactionMain
            }
            val database = DBCache.get(component.dataStoreId.toString()).database

            if (component.userLastModified.isAfter(component.dataLastModified ?: Instant.MIN)) {
                batchDelete(database, component)
                component.data = null
            }

            try {
                val stream = DBHierarchicalXESInputStream(
                    component.dataStoreId.toString(),
                    Query(component.query),
                    false
                )
                val version = stream.readVersion()
                logger.debug("Mining for component $id at version $version")
                val data = ProcessModelComponentData(component)

                if (data.hasModel(version)) {
                    logger.debug(
                        "Component {} is already populated with data for the log version: {}, skipping",
                        id, version
                    )
                    return@transactionMain
                }

                val model = mine(component, stream)
                val autoAccepted = data.addModel(version, store(database, model))
                component.data = data.toJSON()
                component.dataLastModified = Instant.now()
                component.customizationData = updateCustomizationData(model, component.customizationData)
                component.lastError = null
                component.afterCommit {
                    component.triggerEvent(producer, WorkspaceComponentEventType.DataChange, DATA_CHANGE_MODEL)
                    if (autoAccepted)
                        component.triggerEvent(producer, WorkspaceComponentEventType.ModelAccepted) {
                            setLong(MODEL_VERSION, version)
                        }
                }
            } catch (e: Exception) {
                component.lastError = e.message
                logger.warn("Cannot calculate model for component with id $id.", e)
                component.afterCommit {
                    component.triggerEvent(producer, WorkspaceComponentEventType.DataChange, DATA_CHANGE_LAST_ERROR)
                }
            }

        }
    }
}


abstract class DeleteJob<T : ProcessModel> : MinerJob<T> {

    override fun execute(context: JobExecutionContext) = loggedScope { logger ->
        val id = requireNotNull(context.jobDetail.key.name?.toUUID())

        logger.debug("Deleting model for component $id...")

        transactionMain {
            val component = WorkspaceComponent.findById(id)
            if (component === null) {
                logger.error("Component with id $id is not found.")
                return@transactionMain
            }

            try {
                batchDelete(DBCache.get(component.dataStoreId.toString()).database, component)
            } catch (e: Exception) {
                component.lastError = e.message
                logger.warn("Error deleting model for component with id $id.", e)
            }

            if (component.deleted) {
                logger.debug("Deleting component $id...")
                component.delete()
            }
        }
    }

}

/**
 * Base class for all miner services for process models.
 * @property componentType The type of the workspace component that the miner service corresponds to.
 * @property calcJob The class that performs actual mining in a miner-specific way.
 * @property deleteJob The class that deletes all permanently stored data corresponding to the component that is deleted.
 */
abstract class AbstractMinerService(
    schedulerConfig: String,
    private val componentType: ComponentTypeDto,
    private val calcJob: java.lang.Class<out CalcJob<*>>,
    private val deleteJob: java.lang.Class<out DeleteJob<*>>,
) : AbstractJobService(
    schedulerConfig,
    WORKSPACE_COMPONENTS_TOPIC,
    "$WORKSPACE_COMPONENT_TYPE = '$componentType'"
) {
    override fun loadJobs(): List<Pair<JobDetail, Trigger>> = loggedScope {
        val components = transactionMain {
            WorkspaceComponents.slice(WorkspaceComponents.id).select {
                WorkspaceComponents.componentType eq componentType.toString() and WorkspaceComponents.data.isNull()
            }.map { it[WorkspaceComponents.id].value }
        }
        return components.map { createJob(it, calcJob) }
    }

    override fun messageToJobs(message: Message): List<Pair<JobDetail, Trigger>> {
        require(message is MapMessage) { "Unrecognized message $message." }

        val type = ComponentTypeDto.byTypeNameInDatabase(message.getStringProperty(WORKSPACE_COMPONENT_TYPE))
        require(type == componentType) { "Expected $componentType, got $type." }

        val id = message.getString(WORKSPACE_COMPONENT_ID)
        val event = WorkspaceComponentEventType.valueOf(message.getStringProperty(WORKSPACE_COMPONENT_EVENT))

        return when (event) {
            WorkspaceComponentEventType.ComponentCreatedOrUpdated, WorkspaceComponentEventType.NewModelRequired ->
                listOf(createJob(id.toUUID()!!, calcJob))

            WorkspaceComponentEventType.Delete -> listOf(createJob(id.toUUID()!!, deleteJob))
            else -> emptyList() // ignore
        }
    }

    /**
     * @param id of the workspace component
     * @param klass implementing the [Job] to run
     */
    private fun createJob(id: UUID, klass: java.lang.Class<out Job>): Pair<JobDetail, Trigger> = loggedScope {
        val job = JobBuilder
            .newJob(klass)
            .withIdentity(id.toString(), "MinerService/${klass.name}")
            .build()
        val trigger = TriggerBuilder
            .newTrigger()
            .withIdentity(id.toString(), "MinerService/${klass.name}")
            .startNow()
            .build()

        return job to trigger
    }
}
