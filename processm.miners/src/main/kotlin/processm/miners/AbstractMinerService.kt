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
import processm.core.helpers.toUUID
import processm.core.log.hierarchical.DBHierarchicalXESInputStream
import processm.core.logging.loggedScope
import processm.core.models.commons.ProcessModel
import processm.core.persistence.connection.DBCache
import processm.core.persistence.connection.transactionMain
import processm.core.querylanguage.Query
import processm.dbmodels.models.*
import java.time.Instant
import java.util.*


abstract class CalcJob<T : ProcessModel> : ServiceJob {

    abstract fun mine(component: WorkspaceComponent, stream: DBHierarchicalXESInputStream): T
    abstract fun store(database: Database, model: T): String

    override fun execute(context: JobExecutionContext): Unit = loggedScope { logger ->
        val id = requireNotNull(context.jobDetail.key.name?.toUUID())

        logger.debug("Calculating model for component $id...")
        transactionMain {
            val component = WorkspaceComponent.findById(id)
            if (component === null) {
                logger.error("Component with id $id is not found.")
                return@transactionMain
            }

            if (component.data !== null) {
                logger.debug("Component $id is already populated with data, skipping")
                return@transactionMain
            }

            // TODO: store the entire history of models in the component.data field

            try {
                val stream = DBHierarchicalXESInputStream(
                    component.dataStoreId.toString(),
                    Query(component.query),
                    false
                )

                val model = mine(component, stream)
                component.data = store(DBCache.get(component.dataStoreId.toString()).database, model)
                component.dataLastModified = Instant.now()
                component.lastError = null
            } catch (e: Exception) {
                component.lastError = e.message
                logger.warn("Cannot calculate model for component with id $id.", e)
            }
            component.triggerEvent(Producer(), DATA_CHANGE)
        }
    }
}


abstract class DeleteJob : ServiceJob {

    abstract fun delete(database: Database, id: String)

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

                delete(
                    DBCache.get(component.dataStoreId.toString()).database,
                    component.data ?: throw NoSuchElementException("Model does not exist for component $id.")
                )

            } catch (e: Exception) {
                component.lastError = e.message
                logger.warn("Error deleting C-Net for component with id $id.")
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
    private val deleteJob: java.lang.Class<out DeleteJob>,
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
        val event = message.getString(WORKSPACE_COMPONENT_EVENT)

        return when (event) {
            CREATE_OR_UPDATE -> listOf(createJob(id.toUUID()!!, calcJob))
            DELETE -> listOf(createJob(id.toUUID()!!, deleteJob))
            DATA_CHANGE -> emptyList() // ignore
            else -> throw IllegalArgumentException("Unknown event type: $event.")
        }
    }

    /**
     * @param id of the workspace component
     * @param klass implementing the [Job] to run
     */
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
}
