package processm.etl.metamodel

import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.sql.*
import processm.dbmodels.models.*

/**
 * Reads meta model data from the database.
 */
class MetaModelReader(private val dataModelId: Int) {
    private val relatedToDataModel: SqlExpressionBuilder.() -> Op<Boolean> = { Classes.dataModelId eq dataModelId }

    fun getClassId(className: String) =
        MetaModelReaderCache.getClassId(className) {
            Classes.slice(Classes.id).select { Classes.name eq className and relatedToDataModel() }.firstOrNull()?.getOrNull(Classes.id)
            ?: throw NoSuchElementException("A class with the specified name $className does not exist")
        }

    fun getAttributeId(classId: EntityID<Int>, attributeName: String) =
        MetaModelReaderCache.getAttributeId(classId, attributeName) {
            AttributesNames
                .innerJoin(Classes)
                .slice(AttributesNames.id)
                .select { AttributesNames.classId eq classId and (AttributesNames.name eq attributeName) and relatedToDataModel() }
                .firstOrNull()
                ?.getOrNull(AttributesNames.id) ?: throw NoSuchElementException("An attribute with the specified name $attributeName related to class ${classId.value} does not exist")
        }

    fun getLatestObjectVersionId(objectId: String, classId: EntityID<Int>): EntityID<Int>? {
        val relatedToObject: SqlExpressionBuilder.() -> Op<Boolean> = { ObjectVersions.objectId eq objectId and (ObjectVersions.classId eq classId) }

        return ObjectVersions
            .slice(ObjectVersions.id)
            .select {
                    relatedToObject() and (ObjectVersions.startTime eq wrapAsExpression(ObjectVersions.slice(ObjectVersions.startTime.max()).select { relatedToObject() }))
                }
            .map {it[ObjectVersions.id] }
            .firstOrNull()
    }

    fun getObjectVersionsAttributesDelta(newerObjectVersionId: EntityID<Int>, olderObjectVersionId: EntityID<Int>? = null): Map<String, String> {
        val olderOrPreviousObjectVersionId = olderObjectVersionId ?: ObjectVersions
            .slice(ObjectVersions.previousObjectVersionId)
            .select { ObjectVersions.id eq newerObjectVersionId }
            .limit(1)
            .map { it[ObjectVersions.previousObjectVersionId] }
            .firstOrNull() ?: EntityID(-1, ObjectVersions)
        val (newObjectVersionAttributes, olderObjectVersionAttributes ) = AttributesValues
            .innerJoin(AttributesNames)
            .slice(AttributesNames.name, AttributesValues.value, AttributesValues.objectVersionId)
            .select { AttributesValues.objectVersionId inList setOf(newerObjectVersionId, olderOrPreviousObjectVersionId) }
            .partition { it[AttributesValues.objectVersionId] == newerObjectVersionId }
        val olderObjectVersionAttributesMap = olderObjectVersionAttributes
            .map { it[AttributesNames.name] to it[AttributesValues.value] }
            .toMap()

        return newObjectVersionAttributes
            .map { it[AttributesNames.name] to it[AttributesValues.value] }
            .toMap()
            .filter { (attributeName, attributeValue) -> olderObjectVersionAttributesMap[attributeName] != attributeValue }
    }

    fun getRelationships(): Map<String, Pair<EntityID<Int>, EntityID<Int>>> {
        val referencingClassAlias = Classes.alias("referencingClass")

        return Relationships
            .innerJoin(referencingClassAlias, { Relationships.sourceClassId }, { referencingClassAlias[Classes.id] })
            .slice(Relationships.name, Relationships.sourceClassId, Relationships.targetClassId)
            .select { referencingClassAlias[Classes.dataModelId] eq dataModelId }
            .map { it[Relationships.name] to (it[Relationships.sourceClassId] to it[Relationships.targetClassId]) }
            .toMap()
    }

    fun getRelatedObjectsVersionsGroupedByObjects(objectsIds: Set<String>, objectClassId: EntityID<Int>, relatedObjectClassId: EntityID<Int>): Map<String, Map<String, List<EntityID<Int>>>> {
        val relationships = getRelationshipsBetweenClasses(objectClassId, relatedObjectClassId)
        val (relationshipId, relationshipClasses) = relationships.entries.first()
        val isRelatedObjectClassASourceClass = relationshipClasses.first == relatedObjectClassId
        val sourceObjectVersionAlias = ObjectVersions.alias("sourceObjectVersion")
        val targetObjectVersionAlias = ObjectVersions.alias("targetObjectVersion")

        return Relations
            .innerJoin(sourceObjectVersionAlias, { Relations.sourceObjectVersionId }, { sourceObjectVersionAlias[ObjectVersions.id] })
            .innerJoin(targetObjectVersionAlias, { Relations.targetObjectVersionId }, { targetObjectVersionAlias[ObjectVersions.id] })
            .slice(sourceObjectVersionAlias[ObjectVersions.objectId], targetObjectVersionAlias[ObjectVersions.objectId], (if (isRelatedObjectClassASourceClass) sourceObjectVersionAlias else targetObjectVersionAlias)[ObjectVersions.id])
            .select {
                Relations.relationshipId eq relationshipId and
                        ((if (isRelatedObjectClassASourceClass) targetObjectVersionAlias else sourceObjectVersionAlias)[ObjectVersions.objectId] inList objectsIds)
            }
            .groupBy { it[(if (isRelatedObjectClassASourceClass) targetObjectVersionAlias else sourceObjectVersionAlias)[ObjectVersions.objectId]] }
            .mapValues { (_, relatedObjects) ->
                relatedObjects.groupBy({ it[(if (isRelatedObjectClassASourceClass) sourceObjectVersionAlias else targetObjectVersionAlias)[ObjectVersions.objectId]] },
                    { it[(if (isRelatedObjectClassASourceClass) sourceObjectVersionAlias else targetObjectVersionAlias)[ObjectVersions.id]] })
            }
    }

    fun getRelatedObjectsVersions(objectsIds: Set<String>, objectClassId: EntityID<Int>, relatedObjectClassId: EntityID<Int>): Map<String, List<EntityID<Int>>> {
        val relationships = getRelationshipsBetweenClasses(objectClassId, relatedObjectClassId)
        val (relationshipId, relationshipClasses) = relationships.entries.first()
        val isRelatedObjectClassASourceClass = relationshipClasses.first == relatedObjectClassId
        val sourceObjectVersionAlias = ObjectVersions.alias("sourceObjectVersion")
        val targetObjectVersionAlias = ObjectVersions.alias("targetObjectVersion")

        return Relations
            .innerJoin(sourceObjectVersionAlias, { Relations.sourceObjectVersionId }, { sourceObjectVersionAlias[ObjectVersions.id] })
            .innerJoin(targetObjectVersionAlias, { Relations.targetObjectVersionId }, { targetObjectVersionAlias[ObjectVersions.id] })
            .slice(sourceObjectVersionAlias[ObjectVersions.objectId], targetObjectVersionAlias[ObjectVersions.objectId], (if (isRelatedObjectClassASourceClass) sourceObjectVersionAlias else targetObjectVersionAlias)[ObjectVersions.id])
            .select {
                Relations.relationshipId eq relationshipId and
                        ((if (isRelatedObjectClassASourceClass) targetObjectVersionAlias else sourceObjectVersionAlias)[ObjectVersions.objectId] inList objectsIds)
            }
            .groupBy { it[(if (isRelatedObjectClassASourceClass) sourceObjectVersionAlias else targetObjectVersionAlias)[ObjectVersions.objectId]] }
            .mapValues { (_, relatedObjects) ->
                relatedObjects.map { it[(if (isRelatedObjectClassASourceClass) sourceObjectVersionAlias else targetObjectVersionAlias)[ObjectVersions.id]] }
            }
    }

    fun getObjectVersionsRelatedToClass(objectVersionClassId: EntityID<Int>) =
        ObjectVersions
            .slice(ObjectVersions.objectId, ObjectVersions.id)
            .select { ObjectVersions.classId eq objectVersionClassId }
            .groupBy( { it[ObjectVersions.objectId] }, { it[ObjectVersions.id] })

    data class LogEvent(
        val timestamp: Long,
        val changeType: String,
        val className: String,
        val objectId: String,
        val changes: Map<String, String>?) {
        override fun toString(): String = "$$changeType $className($objectId): ${changes?.map { (key, value) -> "$key: $value" }?.joinToString()}"
    }

    fun getTraceData(trace: Map<EntityID<Int>, Map<String, List<EntityID<Int>>>>): List<Pair<Long?, LogEvent>> {
        val objectVersionsIds = trace.values.map { it.values.flatten() }.flatten()
        val objectVersionsAttributesDelta =
            objectVersionsIds.map { it to getObjectVersionsAttributesDelta(it) }.toMap()

        return ObjectVersions.innerJoin(Classes)
            .slice(
                ObjectVersions.id,
                ObjectVersions.startTime,
                ObjectVersions.objectId,
                ObjectVersions.classId,
                ObjectVersions.causingEventType,
                Classes.name)
            .select { ObjectVersions.id inList objectVersionsIds }
            .orderBy(ObjectVersions.startTime)
            .map {
                it[ObjectVersions.startTime] to LogEvent(
                    it[ObjectVersions.startTime]!!,
                    it[ObjectVersions.causingEventType]!!,
                    it[Classes.name],
                    it[ObjectVersions.objectId],
                    objectVersionsAttributesDelta[it[ObjectVersions.id]]
                )
            }
    }

    fun getClassNames() =
        Classes.slice(Classes.id, Classes.name)
            .selectBatched { relatedToDataModel() }
            .flatten()
            .map { it[Classes.id] to it[Classes.name] }
            .toMap()

    private fun getRelationshipsBetweenClasses(firstClassId: EntityID<Int>, secondClassId: EntityID<Int>) =
        Relationships.slice(Relationships.id, Relationships.sourceClassId, Relationships.targetClassId)
            .select { (Relationships.sourceClassId eq firstClassId and (Relationships.targetClassId eq secondClassId)) or
                    (Relationships.sourceClassId eq secondClassId and (Relationships.targetClassId eq firstClassId)) }
            .map { it[Relationships.id] to (it[Relationships.sourceClassId] to it[Relationships.targetClassId]) }
            .toMap()

    private companion object MetaModelReaderCache {
        private val classIds = mutableMapOf<String, EntityID<Int>>()
        private val attributeIds = mutableMapOf<Pair<EntityID<Int>, String>, EntityID<Int>>()

        fun getClassId(className: String, valueGetter: () -> EntityID<Int>) =
            classIds.getOrPut(className, valueGetter)

        fun getAttributeId(classId: EntityID<Int>, attributeName: String, valueGetter: () -> EntityID<Int>) =
            attributeIds.getOrPut(classId to attributeName, valueGetter)
    }
}