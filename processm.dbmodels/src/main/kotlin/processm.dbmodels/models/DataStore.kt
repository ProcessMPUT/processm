package processm.dbmodels.models

import org.jetbrains.exposed.dao.UUIDEntity
import org.jetbrains.exposed.dao.UUIDEntityClass
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.UUIDTable
import org.jetbrains.exposed.sql.javatime.datetime
import java.time.LocalDateTime
import java.util.*

object DataStores : UUIDTable("data_stores") {
    val name = text("name")
    val creationDate = datetime("creation_date")
    val organizationId = reference("organization_id", Organizations)
}

class DataStore(id: EntityID<UUID>) : UUIDEntity(id) {
    companion object : UUIDEntityClass<DataStore>(DataStores)

    var name by DataStores.name
    var creationDate by DataStores.creationDate
    var organization by Organization referencedOn DataStores.organizationId

    fun toDto() = DataStoreDto(id.value, name, creationDate)
}

data class DataStoreDto(val id: UUID, val name: String, val creationDate: LocalDateTime)
