package processm.dbmodels.models

import org.jetbrains.exposed.dao.UUIDEntity
import org.jetbrains.exposed.dao.UUIDEntityClass
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.UUIDTable
import org.jetbrains.exposed.sql.`java-time`.datetime
import java.time.LocalDateTime
import java.util.*

const val DATA_CONNECTOR_TOPIC = "data_connector"
const val TYPE = "type"
const val ACTIVATE = "activate"
const val DEACTIVATE = "deactivate"
const val RELOAD = "reload"
const val DATA_CONNECTOR_ID = "data_connector_id"
const val DATA_STORE_ID = "data_store_id"

object DataConnectors : UUIDTable("data_connectors") {
    val name = text("name")
    val lastConnectionStatus = bool("last_connection_status").nullable()
    val lastConnectionStatusTimestamp = datetime("last_connection_status_timestamp").nullable()
    val connectionProperties = text("connection_properties")
    val dataModelId = reference("data_model_id", DataModels).nullable()

}

class DataConnector(id: EntityID<UUID>) : UUIDEntity(id) {
    companion object : UUIDEntityClass<DataConnector>(DataConnectors)

    var name by DataConnectors.name
    var lastConnectionStatus by DataConnectors.lastConnectionStatus
    var lastConnectionStatusTimestamp by DataConnectors.lastConnectionStatusTimestamp
    var connectionProperties by DataConnectors.connectionProperties
    var dataModel by DataModel optionalReferencedOn DataConnectors.dataModelId

    fun toDto() = DataConnectorDto(id.value, name, lastConnectionStatus, lastConnectionStatusTimestamp, dataModel?.id?.value)
}

data class DataConnectorDto(val id: UUID, val name: String, val lastConnectionStatus: Boolean?, val lastConnectionStatusTimestamp: LocalDateTime?, val dataModelId: Int?, var connectionProperties: Map<String, String>? = null)
