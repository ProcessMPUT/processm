package processm.etl.helpers

import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.sql.Transaction
import processm.dbmodels.models.ETLError
import processm.dbmodels.models.EtlProcessMetadata
import java.util.*

/**
 * Stores an error represented by [e] in the ETL metadata identified by [metadataId]
 */
fun Transaction.reportETLError(metadataId: EntityID<UUID>, e: Throwable) {
    ETLError.new {
        metadata = metadataId
        message = e.message ?: "(not available)"
        exception = e.stackTraceToString()
    }
}

/**
 * Stores an error represented by [e] in the ETL metadata identified by [metadataId]
 */
fun Transaction.reportETLError(metadataId: UUID, e: Throwable) {
    ETLError.new {
        metadata = checkNotNull(EtlProcessMetadata.findById(metadataId)?.id)
        message = e.message ?: "(not available)"
        exception = e.stackTraceToString()
    }
}