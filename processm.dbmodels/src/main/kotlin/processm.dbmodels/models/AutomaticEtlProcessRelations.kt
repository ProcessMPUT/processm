package processm.dbmodels.models

import org.jetbrains.exposed.dao.UUIDEntity
import org.jetbrains.exposed.dao.UUIDEntityClass
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.UUIDTable
import java.util.*

object AutomaticEtlProcessRelations
    : UUIDTable("automatic_etl_processes_relations") {
    val automaticEtlProcessId = reference("automatic_etl_process_id", AutomaticEtlProcesses)
    val sourceClassId = reference("source_class_id", Classes)
    val targetClassId = reference("target_class_id", Classes)
}

class AutomaticEtlProcessRelation(id: EntityID<UUID>) : UUIDEntity(id) {
    companion object : UUIDEntityClass<AutomaticEtlProcessRelation>(AutomaticEtlProcessRelations)

    var automaticEtlProcessRelation by AutomaticEtlProcess referencedOn AutomaticEtlProcessRelations.automaticEtlProcessId
    var sourceClassId by AutomaticEtlProcessRelations.sourceClassId
    var targetClassId by AutomaticEtlProcessRelations.targetClassId
}
