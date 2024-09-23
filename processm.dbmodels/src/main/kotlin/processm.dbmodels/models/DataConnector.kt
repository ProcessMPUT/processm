package processm.dbmodels.models

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.dao.UUIDEntity
import org.jetbrains.exposed.dao.UUIDEntityClass
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.UUIDTable
import org.jetbrains.exposed.sql.javatime.datetime
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

/**
 * Connection properties of a data connector, used both in the DB and in the API in its JSON-serialized form
 * Which fields must be populated depends on [ConnectionType]
 */
@kotlinx.serialization.Serializable
data class ConnectionProperties(
    val connectionType: ConnectionType,
    val connectionString: String? = null,
    val server: String? = null,
    val port: Int? = null,
    val username: String? = null,
    val password: String? = null,
    val database: String? = null,
    val trustServerCertificate: Boolean? = null,
    val https: Boolean? = null,
    val collection: String? = null
)

class DataConnector(id: EntityID<UUID>) : UUIDEntity(id) {
    companion object : UUIDEntityClass<DataConnector>(DataConnectors)

    var name by DataConnectors.name
    var lastConnectionStatus by DataConnectors.lastConnectionStatus
    var lastConnectionStatusTimestamp by DataConnectors.lastConnectionStatusTimestamp

    var connectionProperties by DataConnectors.connectionProperties.transform(Json::encodeToString) {
        Json.decodeFromString<ConnectionProperties>(it)
    }
    var dataModel by DataModel optionalReferencedOn DataConnectors.dataModelId
}

/**
 * Intended to represent "connection-type" in [DataConnector.connectionProperties]
 * It should be kept consistent with `ConnectionType` in `DataStore.ts`
 */
enum class ConnectionType {
    PostgreSql,
    SqlServer,
    MySql,
    OracleDatabase,
    Db2,
    CouchDBProperties,
    MongoDBProperties,
    CouchDBString,
    MongoDBString,
    JdbcString
}