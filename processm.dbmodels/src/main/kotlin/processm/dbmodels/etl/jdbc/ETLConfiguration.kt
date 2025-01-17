package processm.dbmodels.etl.jdbc

import org.jetbrains.exposed.dao.UUIDEntity
import org.jetbrains.exposed.dao.UUIDEntityClass
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.UUIDTable
import org.jetbrains.exposed.sql.ReferenceOption
import processm.dbmodels.models.EtlProcessMetadata
import processm.dbmodels.models.EtlProcessesMetadata
import java.util.*

const val JDBC_ETL_TOPIC = "jdbc_etl"
const val DATASTORE = "datastore"
const val TYPE = "type"
const val ACTIVATE = "activate"
const val DEACTIVATE = "deactivate"
const val TRIGGER = "trigger"
const val ID = "id"

object ETLConfigurations : UUIDTable("etl_configurations") {
    val metadata = reference("metadata", EtlProcessesMetadata, onDelete = ReferenceOption.CASCADE)
    val query = text("query")
    val refresh = long("refresh").nullable()
    val batch = bool("batch").default(false)
    val logIdentityId = uuid("log_identity_id").clientDefault { UUID.randomUUID() }
    val lastEventExternalId = text("last_event_external_id").nullable()
    val lastEventExternalIdType = integer("last_event_external_id_type").nullable()
    val sampleSize = integer("sample_size").nullable()
}

/**
 * A configuration for a JDBC-based ETL process.
 */
class ETLConfiguration(id: EntityID<UUID>) : UUIDEntity(id) {
    companion object : UUIDEntityClass<ETLConfiguration>(ETLConfigurations)

    var metadata by EtlProcessMetadata referencedOn ETLConfigurations.metadata

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
     * The mapping of columns in the remote database into the attributes.
     */
    val columnToAttributeMap by ETLColumnToAttributeMap referrersOn ETLColumnToAttributeMaps.configuration


    /**
     * The maximal size of the final log, as the number of components. Used while testing an ETL process configuration.
     */
    var sampleSize by ETLConfigurations.sampleSize

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

