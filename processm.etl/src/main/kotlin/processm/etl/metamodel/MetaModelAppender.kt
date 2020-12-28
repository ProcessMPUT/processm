package processm.etl.metamodel

import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.sql.*
import processm.etl.tracker.DatabaseChangeApplier

class MetaModelAppender(private val metaModelId: Int, private val metaModelReader: MetaModelReader) {
    fun updateObjectVersionEndTimestamp(objectId: String, classId: EntityID<Int>, endTime: Long) {
        ObjectVersions.update({ ObjectVersions.classId eq classId and (ObjectVersions.objectId eq objectId) and (ObjectVersions.endTime eq null) }) {
            it[ObjectVersions.endTime] = endTime
        }
    }

    fun addObjectVersion(objectId: String, classId: EntityID<Int>, eventType: DatabaseChangeApplier.EventType, startTime: Long?, attributesValues: Map<EntityID<Int>, String>): EntityID<Int> {
        val latestObjectVersionId = metaModelReader.getLatestObjectVersionId(objectId, classId)
        val objectVersionId = ObjectVersions.insertAndGetId {
            it[ObjectVersions.objectId] = objectId
            it[ObjectVersions.previousObjectVersionId] = latestObjectVersionId
            it[ObjectVersions.classId] = classId
            it[ObjectVersions.causingEventType] = eventType.toString().toLowerCase()
            it[ObjectVersions.startTime] = startTime
            it[ObjectVersions.endTime] = null
        }

        addObjectVersionAttributesValues(objectVersionId, attributesValues)
        addObjectVersionRelations(objectVersionId, latestObjectVersionId, attributesValues)

        // TODO: Insert actual process events to be used in the log building phase
        //                val insertedEvents = Events.batchInsert(databaseChangeEventsList.filter { !it.isSnapshot }) { event ->
        //                    this[Events.name] = "${event.eventType} ${event.entityTable}"
        //                    this[Events.resource] = event.entityId
        //                }.map { Event.wrapRow(it) }
        // TODO: add references between object versions and events
        //            EventsToObjectVersions.batchInsert(insertedEvents) { event ->
        //                this[EventsToObjectVersions.eventId] = event.id.value
        //                this[EventsToObjectVersions.objectVersionId] = MetaModelCache.getLatestObjectVersionId(event.entityTable, event.entityId)
        //            }
        //            BatchInsertStatement(Relations).apply {

        //            }

        return objectVersionId
    }

    private fun addObjectVersionRelations(objectVersionId: EntityID<Int>, previousObjectVersionId: EntityID<Int>?, attributesValues: Map<EntityID<Int>, String>) {
        if (previousObjectVersionId != null) {
            Relations.insert(
                Relations.slice(Relations.sourceObjectVersionId, intLiteral(objectVersionId.value), Relations.relationshipId)
                    .select { Relations.targetObjectVersionId eq previousObjectVersionId },
                columns = listOf(Relations.sourceObjectVersionId, Relations.targetObjectVersionId, Relations.relationshipId))
        }

        Relationships
            .slice(Relationships.id, Relationships.targetClassId, Relationships.referencingAttributeNameId)
            .select { Relationships.referencingAttributeNameId inList attributesValues.keys }
            .forEach {
                val relationshipId = it[Relationships.id]
                attributesValues[it[Relationships.referencingAttributeNameId]]?.let { objectId ->
                    metaModelReader.getLatestObjectVersionId(objectId, it[Relationships.targetClassId])?.let { referencedObjectVersionId ->
                        Relations.insert {
                            it[Relations.sourceObjectVersionId] = objectVersionId
                            it[Relations.targetObjectVersionId] = referencedObjectVersionId
                            it[Relations.relationshipId] = relationshipId
                        }
                    }
                }
            }
    }

    private fun addObjectVersionAttributesValues(objectVersionId: EntityID<Int>, attributesValues: Map<EntityID<Int>, String>) {
        AttributesValues.batchInsert(attributesValues.entries) {(attributeId, attributeValue) ->
            this[AttributesValues.objectVersionId] = objectVersionId
            this[AttributesValues.attributeNameId] = attributeId
            this[AttributesValues.value] = attributeValue
        }
    }
}