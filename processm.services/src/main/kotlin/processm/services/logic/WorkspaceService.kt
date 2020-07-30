package processm.services.logic

import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.neq
import org.jetbrains.exposed.sql.transactions.transaction
import processm.core.helpers.mapToArray
import processm.core.models.causalnet.DBSerializer
import processm.core.persistence.DBConnectionPool
import processm.services.api.models.CausalNetComponentData
import processm.services.api.models.ComponentAbstract
import processm.services.api.models.ComponentType
import processm.services.models.*
import processm.services.models.UserGroupWithWorkspaces.userGroupId
import java.util.*

class WorkspaceService(private val accountService: AccountService) {

    fun getUserWorkspaces(userId: UUID, organizationId: UUID) = transaction(DBConnectionPool.database) {
        Workspace.wrapRows(UserGroups
            .innerJoin(UsersInGroups)
            .innerJoin(UserGroupWithWorkspaces)
            .innerJoin(Workspaces)
            .slice(Workspaces.columns)
            .select { UsersInGroups.userId eq userId and (UserGroupWithWorkspaces.organizationId eq organizationId) }).map { it.toDto() }
    }

    fun createWorkspace(workspaceName: String, userId: UUID, organizationId: UUID) = transaction(DBConnectionPool.database) {
        val user = accountService.getAccountDetails(userId)
        val privateGroupId = user.privateGroup.id
        val workspaceId= Workspaces.insertAndGetId {
            it[this.name] = workspaceName
        }

        UserGroupWithWorkspaces.insertAndGetId {
            it[this.workspaceId] = workspaceId
            it[userGroupId] = EntityID(privateGroupId, UserGroups)
            it[this.organizationId] = EntityID(organizationId, Organizations)
        }

        return@transaction workspaceId.value
    }

    fun removeWorkspace(workspaceId: UUID, userId: UUID, organizationId: UUID) = transaction(DBConnectionPool.database) {
        val canBeRemoved = UsersInGroups
            .innerJoin(UserGroups)
            .innerJoin(UserGroupWithWorkspaces)
            .select { UserGroupWithWorkspaces.workspaceId eq workspaceId and (UserGroupWithWorkspaces.organizationId eq organizationId) and (UsersInGroups.userId eq userId) and (UserGroups.groupRoleId neq GroupRoles.getIdByName(GroupRoleDto.Reader)) }
            .limit(1)
            .any()

        if (!canBeRemoved) {
            throw ValidationException(
                ValidationException.Reason.ResourceNotFound, "The specified workspace does not exist or the user has insufficient permissions to it")
        }

        Workspaces.deleteWhere {
            Workspaces.id eq workspaceId
        } > 0
    }

    fun getWorkspaceComponents(workspaceId: UUID) =
        DBSerializer.fetchAll().map {casualNet ->
            val nodes =
                (sequenceOf(casualNet.start) +
                 casualNet.activities.filter { it != casualNet.start && it != casualNet.end } +
                 sequenceOf(casualNet.end))
                    .map { CausalNetNodeDto(
                        it.name,
                        casualNet.splits[it].orEmpty().mapToArray { split -> split.targets.mapToArray { t -> t.name } },
                        casualNet.joins[it].orEmpty().mapToArray { join -> join.sources.mapToArray { s -> s.name } }
                    ) }
                    .toList()
            val edges = casualNet.dependencies.map { CausalNetEdgeDto(it.source.name, it.target.name) }

            WorkspaceComponentDto(UUID.randomUUID(), "component-name", data = CausalNetDto(nodes, edges))
        }
}