package processm.dbmodels.models

import org.jetbrains.exposed.dao.UUIDEntity
import org.jetbrains.exposed.dao.UUIDEntityClass
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.UUIDTable
import org.jetbrains.exposed.sql.`java-time`.datetime
import java.time.LocalDateTime
import java.util.*

object DataConnectors : UUIDTable("data_connectors") {
    val name = text("name")
    val lastConnectionStatus = bool("last_connection_status").nullable()
    val lastConnectionStatusTimestamp = datetime("last_connection_status_timestamp").nullable()
    val connectionProperties = text("connection_properties")
}

class DataConnector(id: EntityID<UUID>) : UUIDEntity(id) {
    companion object : UUIDEntityClass<DataConnector>(DataConnectors)

    var name by DataConnectors.name
    var lastConnectionStatus by DataConnectors.lastConnectionStatus
    var lastConnectionStatusTimestamp by DataConnectors.lastConnectionStatusTimestamp
    var connectionProperties by DataConnectors.connectionProperties

    fun toDto() = DataConnectorDto(id.value, name, lastConnectionStatus, lastConnectionStatusTimestamp)
}

data class DataConnectorDto(val id: UUID, val name: String, val lastConnectionStatus: Boolean?, val lastConnectionStatusTimestamp: LocalDateTime?, var connectionProperties: Map<String, String>? = null)
