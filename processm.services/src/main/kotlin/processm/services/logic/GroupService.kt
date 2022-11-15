package processm.services.logic

import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.exceptions.ExposedSQLException
import org.jetbrains.exposed.sql.*
import processm.core.logging.loggedScope
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
                // FIXME: access control: who can attach/detach users to/from groups?
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
            // FIXME: access control: who can attach/detach users to/from groups?
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
        Group.findById(groupId).validateNotNull(Reason.ResourceNotFound) { "The specified group does not exist" }
    }

    /**
     * Creates a new group.
     */
    fun create(
        name: String,
        parent: UUID? = null,
        organizationId: UUID? = null,
        isImplicit: Boolean = false,
        isShared: Boolean = false
    ): Group = transactionMain {
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
     * Deletes a group with [id].
     * @throws ValidationException if the group does not exist.
     */
    fun remove(id: UUID): Unit = transactionMain {
        Groups.deleteWhere {
            Groups.id eq id
        }.validate(1, Reason.ResourceNotFound) { "Group is not found." }
    }
}
