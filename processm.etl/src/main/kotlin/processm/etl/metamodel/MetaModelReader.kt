package processm.etl.metamodel

import org.jetbrains.exposed.dao.with
import org.jetbrains.exposed.sql.Op
import org.jetbrains.exposed.sql.SqlExpressionBuilder
import org.jetbrains.exposed.sql.selectBatched
import processm.dbmodels.models.Classes
import processm.dbmodels.models.DataModel
import processm.dbmodels.models.Relationship

/**
 * Reads meta model data from the database.
 *
 * TODO this class should be removed
 */
class MetaModelReader(val dataModelId: Int) {
    private fun SqlExpressionBuilder.relatedToDataModel(): Op<Boolean> =
        Classes.dataModelId eq dataModelId

    fun getRelationships(): List<Relationship> {
        return checkNotNull(DataModel.findById(dataModelId)).relationships.with(Relationship::referencingAttributesName)
            .toList()
    }

    fun getClassNames() =
        Classes.slice(Classes.id, Classes.name)
            .selectBatched { relatedToDataModel() }
            .flatten().associate { it[Classes.id] to it[Classes.name] }
}