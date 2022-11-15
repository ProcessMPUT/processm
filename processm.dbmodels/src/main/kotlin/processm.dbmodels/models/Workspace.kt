package processm.dbmodels.models

import org.jetbrains.exposed.dao.UUIDEntity
import org.jetbrains.exposed.dao.UUIDEntityClass
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.UUIDTable
import java.util.*

object Workspaces : UUIDTable("workspaces") {
    val name = text("name")
}

class Workspace(id: EntityID<UUID>) : UUIDEntity(id) {
    companion object : UUIDEntityClass<Workspace>(Workspaces)

    var name by Workspaces.name
}
