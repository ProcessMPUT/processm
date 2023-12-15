package processm.dbmodels.models

import org.jetbrains.exposed.dao.IntEntity
import org.jetbrains.exposed.dao.IntEntityClass
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.IntIdTable
import org.jetbrains.exposed.sql.javatime.datetime

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
}

object Classes : IntIdTable("classes") {
    val name = text("name")
    val dataModelId = reference("data_model_id", DataModels)
}

class Class(id: EntityID<Int>) : IntEntity(id) {
    companion object : IntEntityClass<Class>(Classes)

    var name by Classes.name
    var dataModel by DataModel referencedOn Classes.dataModelId
    val attributesNames by AttributesName referrersOn AttributesNames.classId
}

object DataModels : IntIdTable("data_models") {
    val name = text("name")
    val versionDate = datetime("version_date")
}

class DataModel(id: EntityID<Int>) : IntEntity(id) {
    companion object : IntEntityClass<DataModel>(DataModels)

    var name by DataModels.name
    var versionDate by DataModels.versionDate
    val classes by Class referrersOn Classes.dataModelId
    val relationships by Relationship referrersOn Relationships.dataModelId
}

object Relationships : IntIdTable("relationships") {
    val dataModelId = reference("data_model_id", DataModels)
    val name = text("name")
    val sourceClassId = reference("source_class_id", Classes)
    val targetClassId = reference("target_class_id", Classes)
    val referencingAttributeNameId = reference("referencing_attribute_name_id", AttributesNames)
}

class Relationship(id: EntityID<Int>) : IntEntity(id) {
    companion object : IntEntityClass<Relationship>(Relationships)

    var dataModel by DataModel referencedOn Relationships.dataModelId
    var relationshipName by Relationships.name
    var sourceClass by Class referencedOn Relationships.sourceClassId
    var targetClass by Class referencedOn Relationships.targetClassId
    var referencingAttributesName by AttributesName referencedOn Relationships.referencingAttributeNameId
}