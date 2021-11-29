package processm.dbmodels.models

import org.jetbrains.exposed.dao.UUIDEntity
import org.jetbrains.exposed.dao.UUIDEntityClass
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.UUIDTable
import org.jetbrains.exposed.sql.ReferenceOption
import java.util.*

object AutomaticEtlProcessRelations
    : UUIDTable("automatic_etl_processes_relations") {
    val automaticEtlProcessId = reference("automatic_etl_process_id", AutomaticEtlProcesses)
    val sourceClassId = text("source_class_id")
    val targetClassId = text("target_class_id")
}

class AutomaticEtlProcessRelation(id: EntityID<UUID>) : UUIDEntity(id) {
    companion object : UUIDEntityClass<AutomaticEtlProcessRelation>(AutomaticEtlProcessRelations)

    var automaticEtlProcessRelation by AutomaticEtlProcess referencedOn AutomaticEtlProcessRelations.automaticEtlProcessId
    var sourceClassId by AutomaticEtlProcessRelations.sourceClassId
    var targetClassId by AutomaticEtlProcessRelations.targetClassId

    fun toDto() = AutomaticEtlProcessRelationDto(id.value, automaticEtlProcessRelation.id.value, sourceClassId, targetClassId)
}

data class AutomaticEtlProcessRelationDto(val id: UUID, val automaticEtlProcessId: UUID, val sourceClassId: String, val targetClassId: String)
