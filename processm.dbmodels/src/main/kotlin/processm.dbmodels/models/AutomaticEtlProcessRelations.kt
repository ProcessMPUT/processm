package processm.dbmodels.models

import org.jetbrains.exposed.dao.id.UUIDTable

object AutomaticEtlProcessRelations
    : UUIDTable("automatic_etl_processes_relations") {
    val automaticEtlProcessId = reference("automatic_etl_process_id", AutomaticEtlProcesses)
    val relationship = reference("relationship_id", Relationships)
}
