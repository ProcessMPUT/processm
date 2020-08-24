package processm.services.logic

import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.exceptions.ExposedSQLException
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.transaction
import processm.core.logging.loggedScope
import processm.core.persistence.DBConnectionPool
import processm.dbmodels.models.*
import java.util.*

class GroupService {

    /**
     * Attaches the specified [userId] to the specified [groupId].
     * Throws [ValidationException] if the specified [userId] or [groupId] doesn't exist.
     */
    fun attachUserToGroup(userId: UUID, groupId: UUID): Unit = transaction(DBConnectionPool.database) {
        loggedScope { logger ->
            val userInGroup =
                UsersInGroups.select { UsersInGroups.userId eq userId and (UsersInGroups.groupId eq groupId) }.limit(1)

            if (userInGroup.any()) {
                logger.debug("The user $userId is already assigned to the group $groupId")
                return@transaction
            }

            try {
                UsersInGroups.insert {
                    it[this.userId] = EntityID(userId, Users)
                    it[this.groupId] = EntityID(groupId, UserGroups)
                }
                logger.debug("The user $userId has been successfully assigned to the group $groupId")
            } catch (e: ExposedSQLException) {
                logger.debug("The non-existing userId $userId or groupId $groupId was specified")
                throw ValidationException(
                    ValidationException.Reason.ResourceNotFound, "The specified user or group does not exist"
                )
            }
        }
    }

    /**
     * Returns id of root group for the specified [groupId]. This is the same group that accumulates all users and user groups in a particular organization.
     * Throws [ValidationException] if the specified [groupId] doesn't exist.
     */
    fun getRootGroupId(groupId: UUID) = transaction(DBConnectionPool.database) {
        var parentGroup: ResultRow? = null
        do {
            parentGroup = UserGroups.slice(UserGroups.id, UserGroups.parentGroupId)
            .select { UserGroups.id eq (parentGroup?.getOrNull(UserGroups.parentGroupId) ?: EntityID(groupId, UserGroups)) }
            .firstOrNull()
        } while (parentGroup != null && parentGroup[UserGroups.parentGroupId] != null)

        if (parentGroup == null) {
            throw ValidationException(
                ValidationException.Reason.ResourceNotFound,
                "The specified group does not exist"
            )
        }

        return@transaction parentGroup[UserGroups.id].value
    }

    /**
     * Returns all groups whose direct parent is [groupId].
     * Throws [ValidationException] if the specified [groupId] doesn't exist.
     */
    fun getSubgroups(groupId: UUID) = transaction(DBConnectionPool.database) {
        val userGroup = getGroupDao(groupId)

        userGroup.childGroups.map { it.toDto() }
    }

    /**
     * Returns [UserGroupDto] object for the group with the specified [groupId].
     * Throws [ValidationException] if the specified [groupId] doesn't exist.
     */
    fun getGroup(groupId: UUID) = transaction(DBConnectionPool.database) {
        getGroupDao(groupId).toDto()
    }

    private fun getGroupDao(groupId: UUID) = transaction(DBConnectionPool.database) {
        UserGroup.findById(groupId) ?: throw ValidationException(
            ValidationException.Reason.ResourceNotFound, "The specified group does not exist"
        )
    }
}