package processm.dbmodels.models

import org.jetbrains.exposed.dao.IntEntity
import org.jetbrains.exposed.dao.IntEntityClass
import org.jetbrains.exposed.dao.LongEntity
import org.jetbrains.exposed.dao.LongEntityClass
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.IntIdTable
import org.jetbrains.exposed.dao.id.LongIdTable
import org.jetbrains.exposed.sql.javatime.datetime
import processm.core.log.attribute.Attribute.CONCEPT_NAME
import processm.core.log.attribute.Attribute.ORG_RESOURCE

object AttributesNames : IntIdTable("attributes_names") {
    val name = text("name")
    val type = text("type")
    val isReferencingAttribute = bool("is_referencing_attribute")
    val classId = reference("class_id", Classes)
}

class AttributesName(id: EntityID<Int>) : IntEntity(id) {
    companion object : IntEntityClass<AttributesName>(AttributesNames)

    var name by AttributesNames.name
    var type by AttributesNames.type
    var attributeClass by Class referencedOn AttributesNames.classId
    var isReferencingAttribute by AttributesNames.isReferencingAttribute
    val attributesValues by AttributesValue referrersOn AttributesValues.attributeNameId

    fun toDto() = AttributeNameDto(id.value, name, type, attributeClass.id.value)
}

data class AttributeNameDto(val id: Int, val name: String, val type: String, val classId: Int)

object AttributesValues : IntIdTable("attributes_values") {
    val value = text("value")
    val attributeNameId = reference("attribute_name_id", AttributesNames)
    val objectVersionId = reference("object_version_id", ObjectVersions)
}

class AttributesValue(id: EntityID<Int>) : IntEntity(id) {
    companion object : IntEntityClass<AttributesValue>(AttributesValues)

    val value by AttributesValues.value
    val attributeName by AttributesName referencedOn AttributesValues.attributeNameId
    val objectVersion by ObjectVersion referencedOn AttributesValues.objectVersionId

    fun toDto() = AttributeValueDto(id.value, value, objectVersion.id.value, attributeName.id.value)
}

data class AttributeValueDto(val id: Int, val value: String, val objectVersionId: Int, val attributeNameId: Int)

object Classes : IntIdTable("classes") {
    val name = text("name")
    val dataModelId = reference("data_model_id", DataModels)
}

class Class(id: EntityID<Int>) : IntEntity(id) {
    companion object : IntEntityClass<Class>(Classes)

    var name by Classes.name
    var dataModel by DataModel referencedOn Classes.dataModelId
    val attributesNames by AttributesName referrersOn AttributesNames.classId
    //    val objects by Object referrersOn Objects.classId
    val objects by ObjectVersion referrersOn ObjectVersions.classId

    fun toDto() = ClassDto(id.value, name, dataModel.id.value)
}

data class ClassDto(val id: Int, val name: String, val dataModelId: Int)

object DataModels : IntIdTable("data_models") {
    val name = text("name")
    val versionDate = datetime("version_date")
}

class DataModel(id: EntityID<Int>) : IntEntity(id) {
    companion object : IntEntityClass<DataModel>(DataModels)
    var name by DataModels.name
    var versionDate by DataModels.versionDate
    val classes by Class referrersOn Classes.dataModelId
}

object ObjectVersions : IntIdTable("object_versions") {
    val startTime = long("start_time").nullable()
    val endTime = long("end_time").nullable()
    val previousObjectVersionId = reference("previous_object_version_id", ObjectVersions).nullable()
    val classId = reference("class_id", Classes)
    val objectId = text("object_id")
    val causingEventType = text("causing_event_type").nullable()
    val additionalData = text("additional_data").nullable()
}

class ObjectVersion(id: EntityID<Int>) : IntEntity(id) {
    companion object : IntEntityClass<ObjectVersion>(ObjectVersions)

    val startTime by ObjectVersions.startTime
    val endTime by ObjectVersions.endTime
    var versionClass by Class referencedOn ObjectVersions.classId
    var originalId by ObjectVersions.objectId
    val causingEventType by ObjectVersions.causingEventType
    val attributesValues by AttributesValue referrersOn AttributesValues.objectVersionId
    val relationSource by Relation referrersOn Relations.sourceObjectVersionId
    val relationTarget by Relation referrersOn Relations.targetObjectVersionId
}

object Relations : IntIdTable("relations") {
    val startTime = long("start_time").nullable()
    val endTime = long("end_time").nullable()
    val sourceObjectVersionId = reference("source_object_version_id", ObjectVersions)
    val targetObjectVersionId = reference("target_object_version_id", ObjectVersions)
    val relationshipId = reference("relationship_id", Relationships)
}

class Relation(id: EntityID<Int>) : IntEntity(id) {
    companion object : IntEntityClass<Relation>(Relations)

    val startTime by Relations.startTime
    val endTime by Relations.endTime
    var sourceObjectVersion by ObjectVersion referencedOn Relations.sourceObjectVersionId
    var targetObjectVersion by ObjectVersion referencedOn Relations.targetObjectVersionId
    var relationship by Relationship referencedOn Relations.relationshipId
}

object Relationships : IntIdTable("relationships") {
    val name = text("name")
    val sourceClassId = reference("source_class_id", Classes)
    val targetClassId = reference("target_class_id", Classes)
    val referencingAttributeNameId = reference("referencing_attribute_name_id", AttributesNames)
}

class Relationship(id: EntityID<Int>) : IntEntity(id) {
    companion object : IntEntityClass<Relationship>(Relationships)

    var name by Relationships.name
    var sourceClass by Class referencedOn Relationships.sourceClassId
    var targetClass by Class referencedOn Relationships.targetClassId
    var referencingAttributesName by AttributesName referencedOn Relationships.referencingAttributeNameId
    val relations by Relation referrersOn Relations.relationshipId

    fun toDto() = RelationshipDto(id.value, name, sourceClass.id.value, targetClass.id.value, referencingAttributesName.id.value)
}

data class RelationshipDto(val id: Int, val name: String, val sourceClassId: Int, val targetClassId: Int, val referencingAttributesName: Int)

object Events : LongIdTable("events") {
    val name = text(CONCEPT_NAME).nullable()
    val resource = text(ORG_RESOURCE).nullable()
}

class Event(id: EntityID<Long>) : LongEntity(id) {
    companion object : LongEntityClass<Event>(Events)

    val name by Events.name
    val resource by Events.resource
}

object EventsToObjectVersions : IntIdTable("events_to_object_versions") {
    val objectVersionId = reference("object_version_id", ObjectVersions)
    val eventId = long("event_id")
}

class EventToObjectVersion(id: EntityID<Int>) : IntEntity(id) {
    companion object : IntEntityClass<EventToObjectVersion>(EventsToObjectVersions)

    val objectVersionId by EventsToObjectVersions.objectVersionId
    val eventId by EventsToObjectVersions.eventId
}
