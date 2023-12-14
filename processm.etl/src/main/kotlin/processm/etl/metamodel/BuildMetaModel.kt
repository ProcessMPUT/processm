package processm.etl.metamodel

import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.sql.batchInsert
import org.jetbrains.exposed.sql.insertAndGetId
import org.jetbrains.exposed.sql.statements.BatchInsertStatement
import org.jetbrains.exposed.sql.transactions.transaction
import processm.core.persistence.connection.DBCache
import processm.dbmodels.models.AttributesNames
import processm.dbmodels.models.Classes
import processm.dbmodels.models.DataModels
import processm.dbmodels.models.Relationships
import processm.etl.discovery.DatabaseExplorer

fun buildMetaModel(dataStoreDBName: String, metaModelName: String, databaseExplorer: DatabaseExplorer): EntityID<Int> {
    val classes = databaseExplorer.getClasses()
    val relationships = databaseExplorer.getRelationships()

    return transaction(DBCache.get(dataStoreDBName).database) {
        val dataModelId = DataModels.insertAndGetId {
            it[name] = metaModelName
        }

        val classIds = Classes
            .batchInsert(classes) {
                this[Classes.name] = it.name
                this[Classes.dataModelId] = dataModelId
            }.associate {
                it[Classes.name] to it[Classes.id]
            }

        val referencingAttributeIds = BatchInsertStatement(AttributesNames).apply {
            classes.forEach { metaModelClass ->
                metaModelClass.attributes.forEach { attribute ->
                    classIds[metaModelClass.name]?.let { classId ->
                        addBatch()
                        this[AttributesNames.name] = attribute.name
                        this[AttributesNames.isReferencingAttribute] = attribute.isPartOfForeignKey
                        this[AttributesNames.type] = attribute.type
                        this[AttributesNames.classId] = classId
                    }
                }
            }
            execute(this@transaction)
        }.resultedValues
            .orEmpty()
            .filter { it[AttributesNames.isReferencingAttribute] }
            .associate {
                (it[AttributesNames.classId] to it[AttributesNames.name]) to it[AttributesNames.id]
            }

        BatchInsertStatement(Relationships).apply {
            relationships.forEach { relationship ->
                classIds[relationship.sourceClass.name]?.let { sourceClassId ->
                    classIds[relationship.targetClass.name]?.let { targetClassId ->
                        referencingAttributeIds[sourceClassId to relationship.sourceColumnName]?.let { referencingAttributeId ->
                            addBatch()
                            this[Relationships.name] = relationship.name
                            this[Relationships.sourceClassId] = sourceClassId
                            this[Relationships.targetClassId] = targetClassId
                            this[Relationships.referencingAttributeNameId] = referencingAttributeId
                        }
                    }
                }
            }
            execute(this@transaction)
        }

        return@transaction dataModelId
    }
}