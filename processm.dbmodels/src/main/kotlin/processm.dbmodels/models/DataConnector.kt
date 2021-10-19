package processm.dbmodels.models

import org.jetbrains.exposed.dao.UUIDEntity
import org.jetbrains.exposed.dao.UUIDEntityClass
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.UUIDTable
import java.util.*

object DataConnectors : UUIDTable("data_connectors") {
    val name = text("name")
    val lastConnectionStatus = bool("last_connection_status").nullable()
    val connectionProperties = text("connection_properties")
    val dataStoreId = reference("data_store_id", DataStores)
}

class DataConnector(id: EntityID<UUID>) : UUIDEntity(id) {
    companion object : UUIDEntityClass<DataConnector>(DataConnectors)

    var name by DataConnectors.name
    var lastConnectionStatus by DataConnectors.lastConnectionStatus
    var connectionProperties by DataConnectors.connectionProperties
    val dataStore by DataStore referencedOn DataConnectors.dataStoreId

    fun toDto() = DataConnectorDto(id.value, name, lastConnectionStatus)
}

data class DataConnectorDto(val id: UUID, val name: String, val lastConnectionStatus: Boolean?, var connectionProperties: Map<String, String>? = null)
