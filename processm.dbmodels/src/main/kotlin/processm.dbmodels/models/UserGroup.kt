package processm.dbmodels.models

import org.jetbrains.exposed.dao.UUIDEntity
import org.jetbrains.exposed.dao.UUIDEntityClass
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.UUIDTable
import java.util.*

object UserGroups : UUIDTable("user_groups") {
    val name = text("name").nullable()
    val parentGroupId = reference("parent_group_id", UserGroups).nullable()
    val groupRoleId = reference("group_role_id", GroupRoles)
    val isImplicit = bool("is_implicit")
}

class UserGroup(id: EntityID<UUID>) : UUIDEntity(id) {
    companion object : UUIDEntityClass<UserGroup>(UserGroups)

    var name by UserGroups.name
    var parentGroup by UserGroup optionalReferencedOn UserGroups.parentGroupId
    var groupRole by GroupRole referencedOn UserGroups.groupRoleId
    var isImplicit by UserGroups.isImplicit
    val childGroups by UserGroup optionalReferrersOn UserGroups.parentGroupId
}

