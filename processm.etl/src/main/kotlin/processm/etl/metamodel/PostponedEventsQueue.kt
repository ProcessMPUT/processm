package processm.etl.metamodel

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.dao.IntEntity
import org.jetbrains.exposed.dao.IntEntityClass
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.IntIdTable
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.javatime.timestamp
import org.jetbrains.exposed.sql.select
import processm.dbmodels.models.Classes
import processm.dbmodels.models.Organizations.clientDefault
import processm.etl.tracker.DatabaseChangeApplier
import java.time.Instant
import java.util.*
import kotlin.collections.HashSet

interface PostponedEvent {
    val dbEvent: DatabaseChangeApplier.DatabaseChangeEvent
    val objectId: RemoteObjectID
    val timestamp: Instant
}

data class PostponedEventImpl(
    override val dbEvent: DatabaseChangeApplier.DatabaseChangeEvent,
    override val objectId: RemoteObjectID,
    override val timestamp: Instant
) : PostponedEvent

interface PostponedEventsQueue : Sequence<PostponedEvent> {
    fun add(dbEvent: DatabaseChangeApplier.DatabaseChangeEvent, objectId: RemoteObjectID): PostponedEvent
    fun hasObject(objectID: RemoteObjectID): Boolean

    fun remove(event: PostponedEvent)

    fun isEmpty(): Boolean

    fun isNotEmpty() = !isEmpty()
}

class InMemoryPostponedEventsQueue : PostponedEventsQueue {
    private val queue = LinkedList<PostponedEvent>()
    private val toRemove = HashSet<PostponedEvent>()
    override fun add(dbEvent: DatabaseChangeApplier.DatabaseChangeEvent, objectId: RemoteObjectID): PostponedEvent {
        val event = PostponedEventImpl(dbEvent, objectId, Instant.now())
        queue.add(event)
        return event
    }

    override fun hasObject(objectID: RemoteObjectID): Boolean = queue.any { it !in toRemove && it.objectId == objectID }

    override fun remove(event: PostponedEvent) {
        toRemove.add(event)
    }

    override fun isEmpty(): Boolean {
        if (toRemove.isNotEmpty()) {
            queue.removeAll(toRemove)
            toRemove.clear()
        }
        return queue.isEmpty()
    }

    override fun iterator(): Iterator<PostponedEvent> {
        if (toRemove.isNotEmpty()) {
            queue.removeAll(toRemove)
            toRemove.clear()
        }
        return queue.iterator()
    }
}


internal object PostponedEvents : IntIdTable("postponed_events_queue") {
    // Turns out this cannot be a foreign key since TimescaleDB does not support foreign keys to hypertables.
    // Hence, it makes sense to use log_identity_id instead of integer log_id
    val logIdentityId = uuid("log_identity_id")
    val timestamp = timestamp("timestamp")
    val objectId = text("object_id")
    val classId = reference("class_id", Classes)
    val dbEvent = text("db_event")
}

class DBPostponedEvent(id: EntityID<Int>) : IntEntity(id), PostponedEvent {
    companion object : IntEntityClass<DBPostponedEvent>(PostponedEvents)

    private var rawObjectId by PostponedEvents.objectId
    private var classId by PostponedEvents.classId
    var logIdentityId: UUID by PostponedEvents.logIdentityId
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
    override val timestamp by PostponedEvents.timestamp.clientDefault { Instant.now() }
}

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
        DBPostponedEvent.find { PostponedEvents.logIdentityId eq this@PersistentPostponedEventsQueue.logIdentityId }
            .iterator()
}