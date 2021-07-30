package processm.dbmodels.models

import org.jetbrains.exposed.dao.UUIDEntity
import org.jetbrains.exposed.dao.UUIDEntityClass
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.UUIDTable
import org.jetbrains.exposed.sql.`java-time`.datetime
import java.time.LocalDateTime
import java.util.*

object DataSources : UUIDTable("data_sources") {
    val name = text("name")
    val creationDate = datetime("creation_date")
    val organizationId = reference("organization_id", Organizations)
}

class DataSource(id: EntityID<UUID>) : UUIDEntity(id) {
    companion object : UUIDEntityClass<DataSource>(DataSources)

    var name by DataSources.name
    var creationDate by DataSources.creationDate
    val organization by Organization referencedOn DataSources.organizationId

    fun toDto() = DataSourceDto(id.value, name, creationDate)
}

data class DataSourceDto(val id: UUID, val name: String, val creationDate: LocalDateTime)
