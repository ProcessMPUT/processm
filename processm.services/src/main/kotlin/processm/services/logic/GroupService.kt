package processm.services.logic

import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.exceptions.ExposedSQLException
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.insertAndGetId
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.transactions.transaction
import processm.core.logging.loggedScope
import processm.core.persistence.DBConnectionPool
import processm.services.models.*
import java.util.*

class GroupService {

    fun ensureSharedGroupExists(organizationId: UUID) = transaction(DBConnectionPool.database) {
        val existingGroups =
            UserGroups.select { UserGroups.organizationId eq organizationId and UserGroups.parentGroupId.isNull() }
                .limit(1)

        if (existingGroups.any()) {
            return@transaction existingGroups.first()[UserGroups.id].value
        }

        try {
            UserGroups.insertAndGetId {
                it[this.organizationId] = EntityID(organizationId, Organizations)
                it[groupRoleId] = GroupRoles.getIdByName(GroupRoleDto.Reader)
                it[isImplicit] = true
            }.value
        } catch (e: ExposedSQLException) {
            throw ValidationException(
                ValidationException.Reason.ResourceNotFound,
                "The specified organization does not exist"
            )
        }
    }

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