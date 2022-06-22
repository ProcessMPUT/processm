package processm.dbmodels.models

import org.jetbrains.exposed.dao.UUIDEntity
import org.jetbrains.exposed.dao.UUIDEntityClass
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.UUIDTable
import org.jetbrains.exposed.sql.`java-time`.datetime
import org.jetbrains.exposed.sql.`java-time`.timestamp
import java.time.Instant
import java.time.LocalDateTime
import java.util.*

const val ETL_PROCESS_CONVERSION_TOPIC = "etl_process_conversion"
const val ETL_PROCESS_ID = "etl_process_id"
const val ETL_PROCESS_NAME = "etl_process_name"

object EtlProcessesMetadata : UUIDTable("etl_processes_metadata") {
    val name = text("name")
    val processType = text("process_type")
    val creationDate = datetime("creation_date").clientDefault { LocalDateTime.now() }
    val lastUpdatedDate = datetime("last_updated_date").nullable()
    val dataConnectorId = reference("data_connector_id", DataConnectors)
    val isActive = bool("is_active").default(true)
    val lastExecutionTime = timestamp("last_execution_time").nullable()
}

class EtlProcessMetadata(id: EntityID<UUID>) : UUIDEntity(id) {
    companion object : UUIDEntityClass<EtlProcessMetadata>(EtlProcessesMetadata)

    /**
     * The human-readable name of the configuration.
     */
    var name by EtlProcessesMetadata.name
    var processType by EtlProcessesMetadata.processType
    var creationDate by EtlProcessesMetadata.creationDate
    var lastUpdateDate by EtlProcessesMetadata.lastUpdatedDate

    /**
     * A connector to the remote database.
     */
    var dataConnector by DataConnector referencedOn EtlProcessesMetadata.dataConnectorId
    var isActive by EtlProcessesMetadata.isActive

    /**
     * The date and time of the last execution of the ETL process associated with this configuration.
     */
    var lastExecutionTime by EtlProcessesMetadata.lastExecutionTime

    fun toDto() = EtlProcessMetadataDto(id.value, name, ProcessTypeDto.byNameInDatabase(processType), creationDate, lastUpdateDate, dataConnector.id.value, isActive, lastExecutionTime)
}

data class EtlProcessMetadataDto(val id: UUID, val name: String, val processType: ProcessTypeDto, val creationDate: LocalDateTime, val lastUpdateDate: LocalDateTime?, val dataConnectorId: UUID, val isActive: Boolean, val lastExecutionTime: Instant?)

enum class ProcessTypeDto(val processTypeName: String) {
    Automatic("automatic"), JDBC("jdbc");

    companion object {
        fun byNameInDatabase(nameInDatabase: String) = values().first { it.processTypeName == nameInDatabase }
    }
}
