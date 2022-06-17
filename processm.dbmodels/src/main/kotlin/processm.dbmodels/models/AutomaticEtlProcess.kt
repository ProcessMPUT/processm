package processm.dbmodels.models

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
    val relations by AutomaticEtlProcessRelation referrersOn AutomaticEtlProcessRelations.automaticEtlProcessId

    fun toDto() = AutomaticEtlProcessDto(id.value, etlProcessMetadata.toDto(), relations.map { it.toDto() })
}

data class AutomaticEtlProcessDto(val id: UUID, val metadata: EtlProcessMetadataDto, val relations: List<AutomaticEtlProcessRelationDto>)
