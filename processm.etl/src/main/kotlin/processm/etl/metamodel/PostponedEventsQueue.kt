package processm.etl.metamodel

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.dao.IntEntityClass
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.select
import processm.dbmodels.models.PostponedEvents
import processm.etl.tracker.DatabaseChangeApplier
import java.time.Instant
import java.util.*

interface PostponedEvent {
    val dbEvent: DatabaseChangeApplier.DatabaseChangeEvent
    val objectId: RemoteObjectID
    val timestamp: Instant
}

interface PostponedEventsQueue : Sequence<PostponedEvent> {
    fun add(dbEvent: DatabaseChangeApplier.DatabaseChangeEvent, objectId: RemoteObjectID): PostponedEvent
    fun hasObject(objectID: RemoteObjectID): Boolean
    fun remove(event: PostponedEvent)
    fun isEmpty(): Boolean
    fun isNotEmpty() = !isEmpty()
}

class DBPostponedEvent(id: EntityID<Int>) : processm.dbmodels.models.PostponedEvent(id), PostponedEvent {
    companion object : IntEntityClass<DBPostponedEvent>(PostponedEvents)

    override var dbEvent: DatabaseChangeApplier.DatabaseChangeEvent by PostponedEvents.dbEvent.transform(
        Json::encodeToString,
        Json::decodeFromString
    )
    override var objectId: RemoteObjectID
        get() = RemoteObjectID(rawObjectId, classId)
        set(value) {
            rawObjectId = value.objectId
            classId = value.classId
        }
}

// TODO make sure the queue entries are removed when the corresponding ETL process is removed

class PersistentPostponedEventsQueue(val logIdentityId: UUID) : PostponedEventsQueue {

    override fun add(dbEvent: DatabaseChangeApplier.DatabaseChangeEvent, objectId: RemoteObjectID): PostponedEvent =
        DBPostponedEvent.new {
            this.logIdentityId = this@PersistentPostponedEventsQueue.logIdentityId
            this.objectId = objectId
            this.dbEvent = dbEvent
        }

    override fun hasObject(objectID: RemoteObjectID): Boolean =
        !PostponedEvents
            .select { (PostponedEvents.logIdentityId eq logIdentityId) and (PostponedEvents.classId eq objectID.classId) and (PostponedEvents.objectId eq objectID.objectId) }
            .empty()

    override fun remove(event: PostponedEvent) {
        (event as DBPostponedEvent).delete()
    }

    override fun isEmpty(): Boolean =
        PostponedEvents.select { PostponedEvents.logIdentityId eq logIdentityId }.empty()


    override fun iterator(): Iterator<PostponedEvent> =
        DBPostponedEvent.find { PostponedEvents.logIdentityId eq logIdentityId }
            .iterator()
}