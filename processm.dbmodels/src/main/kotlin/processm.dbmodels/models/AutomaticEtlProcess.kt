package processm.dbmodels.models

import org.jetbrains.exposed.dao.UUIDEntity
import org.jetbrains.exposed.dao.UUIDEntityClass
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.UUIDTable
import org.jetbrains.exposed.sql.ReferenceOption
import org.jetbrains.exposed.sql.`java-time`.datetime
import java.time.LocalDateTime
import java.util.*

object AutomaticEtlProcesses : UUIDTable("automatic_etl_processes") {
}

class AutomaticEtlProcess(id: EntityID<UUID>) : UUIDEntity(id) {
    companion object : UUIDEntityClass<AutomaticEtlProcess>(AutomaticEtlProcesses)

    var etlProcessMetadata by EtlProcessMetadata referencedOn AutomaticEtlProcesses.id
    val relations by AutomaticEtlProcessRelation referrersOn AutomaticEtlProcessRelations.automaticEtlProcessId

    fun toDto() = AutomaticEtlProcessDto(id.value, etlProcessMetadata.toDto(), relations.map { it.toDto() })
}

data class AutomaticEtlProcessDto(val id: UUID, val metadata: EtlProcessMetadataDto, val relations: List<AutomaticEtlProcessRelationDto>)
