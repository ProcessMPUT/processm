package processm.dbmodels.models

import org.jetbrains.exposed.dao.UUIDEntity
import org.jetbrains.exposed.dao.UUIDEntityClass
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.UUIDTable
import java.util.*

object Groups : UUIDTable("groups") {
    val name = text("name").nullable()
    val parentGroupId = reference("parent_group_id", Groups).nullable()

    /**
     * Null for private groups of individual users, non-null for other groups.
     */
    val organizationId = reference("organization_id", Organizations).nullable()

    /**
     * Marks the group corresponding to a user.
     */
    val isImplicit = bool("is_implicit")

    /**
     * Marks the group shared across all organization members.
     */
    val isShared = bool("is_shared")
}

class Group(id: EntityID<UUID>) : UUIDEntity(id) {
    companion object : UUIDEntityClass<Group>(Groups)

    var name by Groups.name
    var parentGroup by Group optionalReferencedOn Groups.parentGroupId
    var organizationId by Organization optionalReferencedOn Groups.organizationId
    var isImplicit by Groups.isImplicit
    var isShared by Groups.isShared
    val childGroups by Group optionalReferrersOn Groups.parentGroupId
    val members by User via UsersInGroups
}

