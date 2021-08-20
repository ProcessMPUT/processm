package processm.dbmodels.etl.jdbc

import org.jetbrains.exposed.dao.UUIDEntity
import org.jetbrains.exposed.dao.UUIDEntityClass
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.UUIDTable
import java.util.*

object ETLConfigurations : UUIDTable("etl_configurations") {
    val name = text("name").uniqueIndex("etl_configurations_name")
    val jdbcUri = text("jdbc_uri")
    val user = text("user").nullable()
    val password = text("password").nullable()
    val query = text("query")
    val refresh = long("refresh").nullable()
    val enabled = bool("enabled").default(true)
    val logIdentityId = uuid("log_identity_id").clientDefault { UUID.randomUUID() }
    val lastEventExternalId = text("last_event_external_id").nullable()
}

class ETLConfiguration(id: EntityID<UUID>) : UUIDEntity(id) {
    companion object : UUIDEntityClass<ETLConfiguration>(ETLConfigurations)

    var name by ETLConfigurations.name
    var jdbcUri by ETLConfigurations.jdbcUri
    var user by ETLConfigurations.user
    var password by ETLConfigurations.password
    var query by ETLConfigurations.query

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

    val columnToAttributeMap by ETLColumnToAttributeMap referrersOn ETLColumnToAttributeMaps.configuration
}

