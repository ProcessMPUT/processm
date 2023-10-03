package processm.etl.helpers

import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.sql.Transaction
import processm.dbmodels.models.ETLError
import java.util.*

fun Transaction.reportETLError(metadataId: EntityID<UUID>, e: Throwable) {
    ETLError.new {
        metadata = metadataId
        message = e.message ?: "(not available)"
        exception = e.stackTraceToString()
    }
}