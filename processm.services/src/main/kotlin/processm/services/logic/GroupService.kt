package processm.services.logic

import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.exceptions.ExposedSQLException
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.transaction
import processm.core.logging.loggedScope
import processm.core.persistence.DBConnectionPool
import processm.services.models.*
import java.util.*

class GroupService {
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
                    ValidationException.Reason.ResourceNotFound, "The specified user or group does not exist")
            }
        }
    }

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

    fun getSubgroups(groupId: UUID) = transaction(DBConnectionPool.database) {
        val userGroup = getGroupDao(groupId)

        userGroup.childGroups.map { it.toDto() }
    }

    fun getGroup(groupId: UUID) = transaction(DBConnectionPool.database) {
        getGroupDao(groupId).toDto()
    }

    private fun getGroupDao(groupId: UUID) = transaction(DBConnectionPool.database) {
        UserGroup.findById(groupId) ?: throw ValidationException(
            ValidationException.Reason.ResourceNotFound, "The specified group does not exist"
        )
    }
}