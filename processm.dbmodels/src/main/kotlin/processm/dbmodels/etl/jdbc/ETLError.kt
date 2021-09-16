package processm.dbmodels.etl.jdbc

import org.jetbrains.exposed.dao.UUIDEntity
import org.jetbrains.exposed.dao.UUIDEntityClass
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.UUIDTable
import org.jetbrains.exposed.sql.ReferenceOption
import org.jetbrains.exposed.sql.`java-time`.timestamp
import java.time.Instant
import java.util.*

/**
 * The collection of errors that occurred when executing the JDBC-based ETL process.
 */
object ETLErrors : UUIDTable("etl_errors") {
    val configuration =
        reference("configuration_id", ETLConfigurations, ReferenceOption.CASCADE, ReferenceOption.CASCADE)
            .index("etl_error_configuration")
    val message = text("message")
    val exception = text("exception").nullable()
    val time = timestamp("time").clientDefault(Instant::now)
}

/**
 * An error that occurred when executing a JDBC-based ETL process.
 */
class ETLError(id: EntityID<UUID>) : UUIDEntity(id) {
    companion object : UUIDEntityClass<ETLError>(ETLErrors)

    /**
     * The configuration associated with the error.
     */
    var configuration by ETLErrors.configuration

    /**
     * The error message.
     */
    var message by ETLErrors.message

    /**
     * The exception with stacktrace.
     */
    var exception by ETLErrors.exception

    /**
     * The date and time of the occurrence.
     */
    var time by ETLErrors.time
}
