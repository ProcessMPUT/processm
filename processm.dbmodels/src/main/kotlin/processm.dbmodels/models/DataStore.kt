package processm.dbmodels.models

import org.jetbrains.exposed.dao.UUIDEntity
import org.jetbrains.exposed.dao.UUIDEntityClass
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.UUIDTable
import org.jetbrains.exposed.sql.javatime.datetime
import java.util.*

object DataStores : UUIDTable("data_stores") {
    val name = text("name")
    val creationDate = datetime("creation_date")
}

class DataStore(id: EntityID<UUID>) : UUIDEntity(id) {
    companion object : UUIDEntityClass<DataStore>(DataStores)

    var name by DataStores.name
    var creationDate by DataStores.creationDate
}