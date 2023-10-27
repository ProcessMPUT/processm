package processm.etl.metamodel

import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.sql.*
import processm.dbmodels.models.*

/**
 * Reads meta model data from the database.
 */
class MetaModelReader(val dataModelId: Int) {
    private fun SqlExpressionBuilder.relatedToDataModel(): Op<Boolean> =
        Classes.dataModelId eq dataModelId

    private val classIds = HashMap<String, EntityID<Int>>()
    private val attributeIds = mutableMapOf<Pair<EntityID<Int>, String>, EntityID<Int>>()

    fun getClassId(className: String): EntityID<Int> =
        classIds.computeIfAbsent(className) {
            Classes.slice(Classes.id).select { Classes.name eq className and relatedToDataModel() }.firstOrNull()
                ?.getOrNull(Classes.id)
                ?: throw NoSuchElementException("A class with the specified name $className does not exist")
        }

    fun getAttributeId(classId: EntityID<Int>, attributeName: String) =
        attributeIds.computeIfAbsent(classId to attributeName) {
            AttributesNames
                .innerJoin(Classes)
                .slice(AttributesNames.id)
                .select { AttributesNames.classId eq classId and (AttributesNames.name eq attributeName) and relatedToDataModel() }
                .firstOrNull()
                ?.getOrNull(AttributesNames.id)
                ?: throw NoSuchElementException("An attribute with the specified name $attributeName related to class ${classId.value} does not exist")
        }

    fun getLatestObjectVersionId(objectId: String, classId: EntityID<Int>): EntityID<Int>? {
        val relatedToObject: SqlExpressionBuilder.() -> Op<Boolean> =
            { ObjectVersions.objectId eq objectId and (ObjectVersions.classId eq classId) }

        return ObjectVersions
            .slice(ObjectVersions.id)
            .select {
                relatedToObject() and (ObjectVersions.startTime eq wrapAsExpression(
                    ObjectVersions.slice(ObjectVersions.startTime.max()).select { relatedToObject() }))
            }
            .map { it[ObjectVersions.id] }
            .firstOrNull()
    }

    private fun getObjectVersionsAttributesDelta(newerObjectVersionId: Int): Map<String, String> {
        // TODO: I am pretty sure this function could be simplified to a single query
        val olderOrPreviousObjectVersionId = ObjectVersions
            .slice(ObjectVersions.previousObjectVersionId)
            .select { ObjectVersions.id eq newerObjectVersionId }
            .limit(1)
            .map { it[ObjectVersions.previousObjectVersionId]?.value }
            .firstOrNull() ?: -1
        val (newObjectVersionAttributes, olderObjectVersionAttributes) = AttributesValues
            .innerJoin(AttributesNames)
            .slice(AttributesNames.name, AttributesValues.value, AttributesValues.objectVersionId)
            .select {
                AttributesValues.objectVersionId inList setOf(
                    newerObjectVersionId,
                    olderOrPreviousObjectVersionId
                )
            }
            .partition { it[AttributesValues.objectVersionId].value == newerObjectVersionId }
        val olderObjectVersionAttributesMap =
            olderObjectVersionAttributes.associate { it[AttributesNames.name] to it[AttributesValues.value] }

        return newObjectVersionAttributes.associate { it[AttributesNames.name] to it[AttributesValues.value] }
            .filter { (attributeName, attributeValue) -> olderObjectVersionAttributesMap[attributeName] != attributeValue }
    }

    fun getRelationships(): Map<String, Pair<EntityID<Int>, EntityID<Int>>> {
        val referencingClassAlias = Classes.alias("referencingClass")

        return Relationships
            .innerJoin(referencingClassAlias, { sourceClassId }, { referencingClassAlias[Classes.id] })
            .slice(Relationships.name, Relationships.sourceClassId, Relationships.targetClassId)
            .select { referencingClassAlias[Classes.dataModelId] eq dataModelId }
            .associate { it[Relationships.name] to (it[Relationships.sourceClassId] to it[Relationships.targetClassId]) }
    }

    data class LogEvent(
        val timestamp: Long,
        val changeType: String,
        val className: String,
        val objectId: String,
        val changes: Map<String, String>?
    ) {
        override fun toString(): String =
            "$$changeType $className($objectId): ${changes?.map { (key, value) -> "$key: $value" }?.joinToString()}"
    }

    fun getTraceData(objectVersionsIds: Set<Int>): List<Pair<Long?, LogEvent>> {
        // TODO querying trace by trace is inefficient. I'd be better to query in batches,
        // TODO even better to move the whole Debezium->XES transformation to the DB or even skip it altogether
        val objectVersionsAttributesDelta =
            objectVersionsIds.associateWith { getObjectVersionsAttributesDelta(it) }

        return ObjectVersions.innerJoin(Classes)
            .slice(
                ObjectVersions.id,
                ObjectVersions.startTime,
                ObjectVersions.objectId,
                ObjectVersions.classId,
                ObjectVersions.causingEventType,
                Classes.name
            )
            .select { ObjectVersions.id inList objectVersionsIds }
            .orderBy(ObjectVersions.startTime)
            .map {
                it[ObjectVersions.startTime] to LogEvent(
                    it[ObjectVersions.startTime]!!,
                    it[ObjectVersions.causingEventType]!!,
                    it[Classes.name],
                    it[ObjectVersions.objectId],
                    objectVersionsAttributesDelta[it[ObjectVersions.id].value]
                )
            }
    }

    fun getClassNames() =
        Classes.slice(Classes.id, Classes.name)
            .selectBatched { relatedToDataModel() }
            .flatten().associate { it[Classes.id] to it[Classes.name] }
}