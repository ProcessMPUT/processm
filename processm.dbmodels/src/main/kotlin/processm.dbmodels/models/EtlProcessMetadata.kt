package processm.dbmodels.models

import org.jetbrains.exposed.dao.UUIDEntity
import org.jetbrains.exposed.dao.UUIDEntityClass
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.UUIDTable
import org.jetbrains.exposed.sql.`java-time`.datetime
import java.time.LocalDateTime
import java.util.*

object EtlProcessesMetadata : UUIDTable("etl_processes_metadata") {
    val name = text("name")
    val processType = text("process_type")
    val creationDate = datetime("creation_date")
    val lastUpdatedDate = datetime("last_updated_date").nullable()
    val dataConnectorId = reference("data_connector_id", DataConnectors)
}

class EtlProcessMetadata(id: EntityID<UUID>) : UUIDEntity(id) {
    companion object : UUIDEntityClass<EtlProcessMetadata>(EtlProcessesMetadata)

    var name by EtlProcessesMetadata.name
    var processType by EtlProcessesMetadata.processType
    var creationDate by EtlProcessesMetadata.creationDate
    var lastUpdateDate by EtlProcessesMetadata.lastUpdatedDate
    var dataConnector by DataConnector referencedOn EtlProcessesMetadata.dataConnectorId

    fun toDto() = EtlProcessMetadataDto(id.value, name, ProcessTypeDto.byNameInDatabase(processType), creationDate, lastUpdateDate, dataConnector.id.value)
}

data class EtlProcessMetadataDto(val id: UUID, val name: String, val processType: ProcessTypeDto, val creationDate: LocalDateTime, val lastUpdateDate: LocalDateTime?, val dataConnectorId: UUID)

enum class ProcessTypeDto(val processTypeName: String) {
    Automatic("automatic"), JDBC("jdbc");

    companion object {
        fun byNameInDatabase(nameInDatabase: String) = values().first { it.processTypeName == nameInDatabase }
    }
}
