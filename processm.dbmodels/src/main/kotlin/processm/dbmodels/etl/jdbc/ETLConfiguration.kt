package processm.dbmodels.etl.jdbc

import org.jetbrains.exposed.dao.UUIDEntity
import org.jetbrains.exposed.dao.UUIDEntityClass
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.UUIDTable
import org.jetbrains.exposed.sql.`java-time`.timestamp
import java.util.*

const val JDBC_ETL_TOPIC = "jdbc_etl"
const val DATASTORE = "datastore"
const val TYPE = "type"
const val ACTIVATE = "activate"
const val DEACTIVATE = "deactivate"
const val ID = "id"

object ETLConfigurations : UUIDTable("etl_configurations") {
    val name = text("name").uniqueIndex("etl_configurations_name")
    val jdbcUri = text("jdbc_uri")
    val user = text("user").nullable()
    val password = text("password").nullable()
    val query = text("query")
    val refresh = long("refresh").nullable()
    val enabled = bool("enabled").default(true)
    val batch = bool("batch").default(false)
    val logIdentityId = uuid("log_identity_id").clientDefault { UUID.randomUUID() }
    val lastEventExternalId = text("last_event_external_id").nullable()
    val lastEventExternalIdType = integer("last_event_external_id_type").nullable()
    val lastExecutionTime = timestamp("last_execution_time").nullable()
}

/**
 * A configuration for a JDBC-based ETL process.
 */
class ETLConfiguration(id: EntityID<UUID>) : UUIDEntity(id) {
    companion object : UUIDEntityClass<ETLConfiguration>(ETLConfigurations)

    /**
     * The human-readable name of the configuration.
     */
    var name by ETLConfigurations.name

    /**
     * The JDBC URI of the remote database.
     */
    var jdbcUri by ETLConfigurations.jdbcUri

    /**
     * The user of the remote database.
     */
    var user by ETLConfigurations.user

    /**
     * The password to the remote database.
     */
    var password by ETLConfigurations.password

    /**
     * The query retrieving events from the remote database.
     *
     * If it uses a binding variable, [lastEventExternalId] must be initialized to a correct value and [batch] must be set to `false`.
     * Otherwise, [batch] must be set to `true` and [lastEventExternalId] should be set to `null`.
     */
    var query by ETLConfigurations.query

    /**
     * True if the [query] is a batch query and thus does not require a binding variable.
     */
    var batch by ETLConfigurations.batch

    /**
     * Refresh time in seconds. null when disabled.
     */
    var refresh by ETLConfigurations.refresh

    /**
     * Controls whether the ETL process executes.
     */
    var enabled by ETLConfigurations.enabled

    /**
     * The value of the "identity:id" attribute of the log to write to.
     */
    var logIdentityId by ETLConfigurations.logIdentityId

    /**
     * The id of the last fetched event from the external system.
     */
    var lastEventExternalId by ETLConfigurations.lastEventExternalId

    /**
     * The JDBC data type of [lastEventExternalId].
     */
    var lastEventExternalIdType by ETLConfigurations.lastEventExternalIdType

    /**
     * The date and time of the last execution of the ETL process associated with this configuration.
     */
    var lastExecutionTime by ETLConfigurations.lastExecutionTime

    /**
     * The mapping of columns in the remote database into the attributes.
     */
    val columnToAttributeMap by ETLColumnToAttributeMap referrersOn ETLColumnToAttributeMaps.configuration

    /**
     * The log of errors that occurred during executing the ETL process associated with this configuration.
     */
    val errors by ETLError referrersOn ETLErrors.configuration

    /**
     * A flag indicating that this configuration is to be removed.
     */
    var deleted: Boolean = false
        private set

    override fun delete() {
        super.delete()
        deleted = true
    }
}
