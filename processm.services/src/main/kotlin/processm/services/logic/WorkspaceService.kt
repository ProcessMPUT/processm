package processm.services.logic

import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.transactions.transaction
import processm.core.helpers.mapToArray
import processm.core.logging.loggedScope
import processm.core.models.causalnet.DBSerializer
import processm.core.models.causalnet.MutableCausalNet
import processm.core.persistence.DBConnectionPool
import processm.services.models.*
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

    // TODO: methods related to workspace component are missing authorization logic
    fun getWorkspaceComponents(workspaceId: UUID) = loggedScope { logger ->
        transaction(DBConnectionPool.database) {
            WorkspaceComponent.wrapRows(
                WorkspaceComponents.select(WorkspaceComponents.workspaceId eq workspaceId))
                .fold(mutableListOf<WorkspaceComponentDto>()) { acc, component ->
                    val componentDto = component.toDto()

                    if (component.componentType == ComponentTypeDto.CausalNet) {
                        try {
                            componentDto.data = DBSerializer.fetch(component.componentDataSourceId).toDto()
                            acc.add(componentDto)
                        } catch (ex: NoSuchElementException) {
                            logger.warn("The data source ${component.componentDataSourceId} of ${component.componentType} workspace component ${component.id} does not exist")
                        }
                    }
                    else {
                        acc.add(componentDto)
                    }

                    return@fold acc
                }.toList()
        }
    }

    fun updateWorkspaceComponent(workspaceComponentId: UUID, name: String?, componentType: ComponentTypeDto?, customizationData: String? = null): Unit = transaction(DBConnectionPool.database) {
        WorkspaceComponents.update({ WorkspaceComponents.id eq workspaceComponentId }) {
            if (name != null) it[WorkspaceComponents.name] = name
            if (componentType != null) it[WorkspaceComponents.componentType] = componentType.typeName
            if (customizationData != null) it[WorkspaceComponents.customizationData] = customizationData
        }
    }

    private fun MutableCausalNet.toDto(): CausalNetDto {
        val nodes =
            (sequenceOf(start) +
                    activities.filter { it != start && it != end } +
                    sequenceOf(end))
                .map { CausalNetNodeDto(
                    it.name,
                    splits[it].orEmpty().mapToArray { split -> split.targets.mapToArray { t -> t.name } },
                    joins[it].orEmpty().mapToArray { join -> join.sources.mapToArray { s -> s.name } }
                ) }
                .toList()
        val edges = dependencies.map { CausalNetEdgeDto(it.source.name, it.target.name) }

        return CausalNetDto(nodes, edges)
    }
}