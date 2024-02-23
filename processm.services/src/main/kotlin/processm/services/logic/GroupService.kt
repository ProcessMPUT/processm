package processm.services.logic

import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.load
import org.jetbrains.exposed.exceptions.ExposedSQLException
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import processm.core.logging.loggedScope
import processm.core.models.metadata.URN
import processm.core.persistence.connection.transactionMain
import processm.dbmodels.models.*
import java.util.*

class GroupService {

    /**
     * Attaches the specified [userId] to the specified [groupId].
     * Throws [ValidationException] if the specified [userId] or [groupId] doesn't exist.
     */
    fun attachUserToGroup(userId: UUID, groupId: UUID): Unit =
        loggedScope { logger ->
            transactionMain {
                val userInGroup =
                    UsersInGroups.select { UsersInGroups.userId eq userId and (UsersInGroups.groupId eq groupId) }
                        .limit(1)

                if (userInGroup.any()) {
                    logger.debug("The user $userId is already assigned to the group $groupId")
                    return@transactionMain
                }

                try {
                    UsersInGroups.insert {
                        it[this.userId] = EntityID(userId, Users)
                        it[this.groupId] = EntityID(groupId, Groups)
                    }
                    logger.debug("The user $userId has been successfully assigned to the group $groupId")
                } catch (e: ExposedSQLException) {
                    logger.debug("The non-existing userId $userId or groupId $groupId was specified")
                    throw ValidationException(
                        Reason.ResourceNotFound, "The specified user or group does not exist."
                    )
                }
            }
        }

    fun detachUserFromGroup(userId: UUID, groupId: UUID): Unit = loggedScope { logger ->
        transactionMain {
            val group = Group.findById(groupId).validateNotNull(Reason.ResourceNotFound)
            group.isShared.validateNot { "Cannot detach a user from a shared group" }
            group.isImplicit.validateNot { "Cannot detach a user from their implicit group" }
            UsersInGroups.deleteWhere {
                (UsersInGroups.userId eq userId) and (UsersInGroups.groupId eq groupId)
            }.validate(1, Reason.ResourceNotFound) { "The specified user or group is not found." }
        }
    }

    /**
     * Returns id of root group for the specified [groupId]. This is the same group that accumulates all users and user groups in a particular organization.
     * Throws [ValidationException] if the specified [groupId] doesn't exist.
     */
    fun getRootGroupId(groupId: UUID) = transactionMain {
        var parentGroup: ResultRow? = null
        do {
            parentGroup = Groups.slice(Groups.id, Groups.parentGroupId)
                .select {
                    Groups.id eq (parentGroup?.getOrNull(Groups.parentGroupId) ?: EntityID(
                        groupId,
                        Groups
                    ))
                }
                .firstOrNull()
        } while (parentGroup != null && parentGroup[Groups.parentGroupId] != null)

        if (parentGroup == null) {
            throw ValidationException(
                Reason.ResourceNotFound,
                "The specified group does not exist"
            )
        }

        return@transactionMain parentGroup[Groups.id].value
    }

    /**
     * Returns all groups whose direct parent is [groupId].
     * Throws [ValidationException] if the specified [groupId] doesn't exist.
     */
    fun getSubgroups(groupId: UUID): List<Group> = transactionMain {
        val userGroup = getGroup(groupId)
        userGroup.childGroups.toList()
    }

    /**
     * Returns [UserGroupDto] object for the group with the specified [groupId].
     * Throws [ValidationException] if the specified [groupId] doesn't exist.
     */
    fun getGroup(groupId: UUID): Group = transactionMain {
        Group.findById(groupId).validateNotNull(Reason.ResourceNotFound) { "The specified group does not exist." }
            .load(Group::members)
    }

    /**
     * Creates a new group.
     * For user's implicit group set [name] to user account name, [organizationId] to null, and [isShared] to false.
     * For organization's shared group set [name] to organization name, [organizationId] to non-null, and [isShared] to true.
     * For other groups set [organizationId] to non-null and [isShared] to false.
     */
    fun create(
        name: String,
        parent: UUID? = null,
        organizationId: UUID? = null,
        isShared: Boolean = false
    ): Group = transactionMain {
        name.validateNot("", Reason.ResourceFormatInvalid) { "The group name must not be empty." }
        (organizationId !== null || !isShared).validate(Reason.ResourceFormatInvalid)

        val isImplicit = organizationId === null
        Group.new {
            this.name = name
            this.parentGroup = parent?.let { getGroup(parent) }
            this.organizationId = organizationId?.let { Organization[it] }
            this.isImplicit = isImplicit
            this.isShared = isShared
        }
    }

    /**
     * Updates an existing group.
     * @throws ValidationException if the group does not exist.
     */
    fun update(id: UUID, update: (Group.() -> Unit)): Unit = transactionMain {
        val group = Group[id].validateNotNull { "Group is not found." }
        group.update()
    }

    /**
     * Returns a list of [URN]s such that the group identified by the given [groupId] is their sole owner (i.e.,
     * the only entry in the ACL of that URN with [RoleType.Owner] refers to this group)
     */
    fun getSoleOwnershipURNs(groupId: UUID): List<URN> = transactionMain {
        val owner = RoleType.Owner.role.id
        val acl1 = AccessControlList.alias("acl1")
        val acl2 = AccessControlList.alias("acl2")
        acl1
            .slice(acl1[AccessControlList.urn.column])
            .select {
                (acl1[AccessControlList.role_id] eq owner) and
                        (acl1[AccessControlList.group_id] eq groupId) and
                        notExists(acl2.select {
                            (acl2[AccessControlList.group_id] neq groupId) and
                                    (acl2[AccessControlList.role_id] eq owner) and
                                    (acl1[AccessControlList.urn.column] eq acl2[AccessControlList.urn.column])
                        })
            }
            .map { URN(it[acl1[AccessControlList.urn.column]]) }
    }

    /**
     * Deletes a group with [id]. Removes all the related ACLs and all the objects that the group was the sole owner.
     * @throws ValidationException if the group does not exist, is an implicit group or a shared group, or is a sole owner of an object.
     */
    fun remove(id: UUID): Unit = transactionMain {
        getSoleOwnershipURNs(id).isEmpty()
            .validate(Reason.UnprocessableResource) { "The group is a sole owner of an object" }
        AccessControlList.deleteWhere { group_id eq id }
        UsersInGroups.deleteWhere { groupId eq id }
        Groups.deleteWhere {
            (Groups.id eq id) and (isShared eq false) and (isImplicit eq false)
        }.validate(1, Reason.ResourceNotFound) { "Group is not found." }
    }
}

