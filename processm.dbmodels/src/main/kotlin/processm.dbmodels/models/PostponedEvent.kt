package processm.dbmodels.models

import org.jetbrains.exposed.dao.IntEntity
import org.jetbrains.exposed.dao.IntEntityClass
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.IntIdTable
import org.jetbrains.exposed.sql.javatime.timestamp
import java.time.Instant
import java.util.*

object PostponedEvents : IntIdTable("postponed_events_queue") {
    val logIdentityId = uuid("log_identity_id")
    val timestamp = timestamp("timestamp").clientDefault { Instant.now() }
    val objectId = text("object_id")
    val classId = reference("class_id", Classes)
    val dbEvent = text("db_event")
}

open class PostponedEvent(id: EntityID<Int>) : IntEntity(id) {
    companion object : IntEntityClass<PostponedEvent>(PostponedEvents)

    protected var rawObjectId by PostponedEvents.objectId
    protected var classId by PostponedEvents.classId
    var logIdentityId: UUID by PostponedEvents.logIdentityId
    val timestamp by PostponedEvents.timestamp
}