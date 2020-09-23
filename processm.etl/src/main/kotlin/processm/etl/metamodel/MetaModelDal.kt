package processm.etl.metamodel

import org.jetbrains.exposed.dao.IntEntity
import org.jetbrains.exposed.dao.IntEntityClass
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.IntIdTable
import org.jetbrains.exposed.sql.jodatime.datetime

object AttributesNames : IntIdTable("attributes_names") {
    val name = text("name")
    val type = text("type")
    val classId = reference("class_id", Classes)
}

class AttributesName(id: EntityID<Int>) : IntEntity(id) {
    companion object : IntEntityClass<AttributesName>(AttributesNames)

    val name by AttributesNames.name
    val type by AttributesNames.type
    val attributeClass by Class referencedOn AttributesNames.classId
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

    val name by Classes.name
    val dataModel by DataModel referencedOn Classes.dataModelId
    val attributesNames by AttributesName referrersOn AttributesNames.classId
    val objects by Object referrersOn Objects.classId

    fun toDto() = ClassDto(id.value, name, dataModel.id.value)
}

data class ClassDto(val id: Int, val name: String, val dataModelId: Int)

object DataModels : IntIdTable("data_models") {
    val name = text("name")
    val versionDate = datetime("version_date")
}

class DataModel(id: EntityID<Int>) : IntEntity(id) {
    companion object : IntEntityClass<DataModel>(DataModels)

    val name by DataModels.name
    val versionDate by DataModels.versionDate
    val classes by Class referrersOn Classes.dataModelId
}

object ObjectVersions : IntIdTable("object_versions") {
    val startTime = datetime("start_time")
    val endTime = datetime("end_time")
    val objectId = reference("object_id", Objects)
}

class ObjectVersion(id: EntityID<Int>) : IntEntity(id) {
    companion object : IntEntityClass<ObjectVersion>(ObjectVersions)

    val startTime by ObjectVersions.startTime
    val endTime by ObjectVersions.endTime
    val versionObject by Object referencedOn ObjectVersions.objectId
    val attributesValues by AttributesValue referrersOn AttributesValues.objectVersionId
    val relationSource by Relation referrersOn Relations.sourceObjectVersionId
    val relationTarget by Relation referrersOn Relations.targetObjectVersionId
}

object Objects : IntIdTable("objects") {
    val classId = reference("class_id", Classes)
}

class Object(id: EntityID<Int>) : IntEntity(id) {
    companion object : IntEntityClass<Object>(Objects)

    val classId by Objects.classId
    val objectClass by Class referencedOn Objects.classId
    val objectVersion by ObjectVersion referrersOn ObjectVersions.objectId
}

object Relations : IntIdTable("relations") {
    val startTime = datetime("start_time")
    val endTime = datetime("end_time")
    val sourceObjectVersionId = reference("source_object_version_id", ObjectVersions)
    val targetObjectVersionId = reference("target_object_version_id", ObjectVersions)
    val relationshipId = reference("relationship_id", Relationships)
}

class Relation(id: EntityID<Int>) : IntEntity(id) {
    companion object : IntEntityClass<Relation>(Relations)

    val startTime by Relations.startTime
    val endTime by Relations.endTime
    val sourceObjectVersion by ObjectVersion referencedOn Relations.sourceObjectVersionId
    val targetObjectVersion by ObjectVersion referencedOn Relations.targetObjectVersionId
    val relationship by Relationship referencedOn Relations.relationshipId
}

object Relationships : IntIdTable("relationships") {
    val name = text("name")
    val sourceClassId = reference("source_class_id", Classes)
    val targetClassId = reference("target_class_id", Classes)
}

class Relationship(id: EntityID<Int>) : IntEntity(id) {
    companion object : IntEntityClass<Relationship>(Relationships)

    val name by Relationships.name
    val sourceClass by Class referencedOn Relationships.sourceClassId
    val targetClass by Class referencedOn Relationships.targetClassId
    val relations by Relation referrersOn Relations.relationshipId

    fun toDto() = RelationshipDto(id.value, name, sourceClass.id.value, targetClass.id.value)
}

data class RelationshipDto(val id: Int, val name: String, val sourceClassId: Int, val targetClassId: Int)
