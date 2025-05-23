package processm.dbmodels.models

import org.jetbrains.exposed.dao.Entity
import org.jetbrains.exposed.dao.EntityClass
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.IdTable
import org.jetbrains.exposed.sql.Column
import java.util.*

object UsersInGroups : IdTable<UUID>("users_in_groups") {
    val userId = reference("user_id", Users)
    val groupId = reference("group_id", Groups)
    override val primaryKey = PrimaryKey(userId, groupId)
    override val id: Column<EntityID<UUID>>
        get() = groupId
}

class UsersInGroup(userId: EntityID<UUID>) : Entity<UUID>(userId) {
    companion object : EntityClass<UUID, UsersInGroup>(UsersInGroups)

    val user by User referencedOn UsersInGroups.userId
    val group by Group referencedOn UsersInGroups.groupId
}

