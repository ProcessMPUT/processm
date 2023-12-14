package processm.dbmodels.models

import org.jetbrains.exposed.dao.UUIDEntity
import org.jetbrains.exposed.dao.UUIDEntityClass
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.UUIDTable
import org.jetbrains.exposed.sql.ReferenceOption
import org.jetbrains.exposed.sql.javatime.timestamp
import processm.dbmodels.etl.jdbc.ETLConfigurations
import java.time.Instant
import java.util.*

/**
 * The collection of errors that occurred when executing an ETL process.
 */
object ETLErrors : UUIDTable("etl_errors") {
    val metadata =
        reference("metadata_id", EtlProcessesMetadata, ReferenceOption.CASCADE, ReferenceOption.CASCADE)
            .index("etl_error_metadata")
    val message = text("message")
    val exception = text("exception").nullable()
    val time = timestamp("time").clientDefault(Instant::now)
}

/**
 * An error that occurred when executing an ETL process.
 */
class ETLError(id: EntityID<UUID>) : UUIDEntity(id) {
    companion object : UUIDEntityClass<ETLError>(ETLErrors)

    /**
     * The metadata associated with the error.
     */
    var metadata by ETLErrors.metadata

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

    fun toDto() = ETLErrorDto(metadata.value, message, exception, time)
}

data class ETLErrorDto(val metadataId:UUID, val message:String, val exception: String?, val time:Instant)
