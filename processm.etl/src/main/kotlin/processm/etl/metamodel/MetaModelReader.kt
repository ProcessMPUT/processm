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

    fun getRelationships(): Map<String, Pair<EntityID<Int>, EntityID<Int>>> {
        val referencingClassAlias = Classes.alias("referencingClass")

        return Relationships
            .innerJoin(referencingClassAlias, { sourceClassId }, { referencingClassAlias[Classes.id] })
            .slice(Relationships.name, Relationships.sourceClassId, Relationships.targetClassId)
            .select { referencingClassAlias[Classes.dataModelId] eq dataModelId }
            .associate { it[Relationships.name] to (it[Relationships.sourceClassId] to it[Relationships.targetClassId]) }
    }

    fun getClassNames() =
        Classes.slice(Classes.id, Classes.name)
            .selectBatched { relatedToDataModel() }
            .flatten().associate { it[Classes.id] to it[Classes.name] }
}