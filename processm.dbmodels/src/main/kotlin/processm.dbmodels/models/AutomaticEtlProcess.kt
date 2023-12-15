package processm.dbmodels.models

import org.jetbrains.exposed.dao.InnerTableLink
import org.jetbrains.exposed.dao.UUIDEntity
import org.jetbrains.exposed.dao.UUIDEntityClass
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.IdTable
import java.util.*

object AutomaticEtlProcesses : IdTable<UUID>("automatic_etl_processes") {
    override val id = reference("id", EtlProcessesMetadata)
    override val primaryKey = PrimaryKey(id)
}

class AutomaticEtlProcess(id: EntityID<UUID>) : UUIDEntity(id) {
    companion object : UUIDEntityClass<AutomaticEtlProcess>(AutomaticEtlProcesses)

    var etlProcessMetadata by EtlProcessMetadata referencedOn AutomaticEtlProcesses.id

    /**
     * Many-to-many relation between `automatic_etl_processes` and `relationships` via `automatic_etl_processes_relations`
     *
     *  `Relationship.via` should suffice, but it seems that because `automatic_etl_processes` and `etl_processes_metadata` share the ID column, Exposed chooses an incorrect table
     */
    val relations by InnerTableLink(
        AutomaticEtlProcessRelations,
        AutomaticEtlProcesses,
        Relationship,
        AutomaticEtlProcessRelations.automaticEtlProcessId,
        AutomaticEtlProcessRelations.relationship
    )
}
