package processm.services.logic

import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.statements.BatchUpdateStatement
import org.jetbrains.exposed.sql.transactions.transaction
import processm.core.helpers.mapToArray
import processm.core.logging.loggedScope
import processm.core.models.causalnet.DBSerializer
import processm.core.models.causalnet.MutableCausalNet
import processm.core.persistence.connection.DBCache
import processm.dbmodels.models.*
import java.util.*

class WorkspaceService(private val accountService: AccountService) {

    /**
     * Returns all user workspaces for the specified [userId] in the context of the specified [organizationId].
     */
    fun getUserWorkspaces(userId: UUID, organizationId: UUID) = transaction(DBCache.getMainDBPool().database) {
        Workspace.wrapRows(UserGroups
            .innerJoin(UsersInGroups)
            .innerJoin(UserGroupWithWorkspaces)
            .innerJoin(Workspaces)
            .slice(Workspaces.columns)
            .select { UsersInGroups.userId eq userId and (UserGroupWithWorkspaces.organizationId eq organizationId) })
            .map { it.toDto() }
    }

    /**
     * Creates new workspace with [workspaceName] in the context of specified [organizationId] and assigns it to private group of the specified [userId].
     */
    fun createWorkspace(workspaceName: String, userId: UUID, organizationId: UUID) =
        transaction(DBCache.getMainDBPool().database) {
            val user = accountService.getAccountDetails(userId)
            val privateGroupId = user.privateGroup.id
            val workspaceId = Workspaces.insertAndGetId {
                it[this.name] = workspaceName
            }

            UserGroupWithWorkspaces.insertAndGetId {
                it[this.workspaceId] = workspaceId
                it[userGroupId] = EntityID(privateGroupId, UserGroups)
                it[this.organizationId] = EntityID(organizationId, Organizations)
            }

            return@transaction workspaceId.value
        }

    /**
     * Removes the specified [workspaceId].
     * Throws [ValidationException] if the specified [userId] has insufficient permissions or the [workspaceId] doesn't exist.
     */
    fun removeWorkspace(workspaceId: UUID, userId: UUID, organizationId: UUID) =
        transaction(DBCache.getMainDBPool().database) {
            val canBeRemoved = UsersInGroups
                .innerJoin(UserGroups)
                .innerJoin(UserGroupWithWorkspaces)
                .select {
                    UserGroupWithWorkspaces.workspaceId eq workspaceId and (UserGroupWithWorkspaces.organizationId eq organizationId) and (UsersInGroups.userId eq userId) and (UserGroups.groupRoleId neq GroupRoles.getIdByName(
                        GroupRoleDto.Reader
                    ))
                }
                .limit(1)
                .any()

            if (!canBeRemoved) {
                throw ValidationException(
                    ValidationException.Reason.ResourceNotFound,
                    "The specified workspace does not exist or the user has insufficient permissions to it"
                )
            }

            Workspaces.deleteWhere {
                Workspaces.id eq workspaceId
            } > 0
        }

    /**
     * Returns all components in the specified [workspaceId].
     */
    fun getWorkspaceComponents(workspaceId: UUID, userId: UUID, organizationId: UUID) = loggedScope { logger ->
        transaction(DBCache.getMainDBPool().database) {
            WorkspaceComponent.wrapRows(
                WorkspaceComponents
                    .innerJoin(Workspaces)
                    .innerJoin(UserGroupWithWorkspaces)
                    .innerJoin(UserGroups)
                    .innerJoin(UsersInGroups)
                    .select(WorkspaceComponents.workspaceId eq workspaceId and (UserGroupWithWorkspaces.organizationId eq organizationId) and (UsersInGroups.userId eq userId))
            )
                .fold(mutableListOf<WorkspaceComponentDto>()) { acc, component ->
                    val componentDto = component.toDto()

                    if (component.componentType == ComponentTypeDto.CausalNet && component.componentDataStoreId != null) {
                        try {
                            componentDto.data =
                                DBSerializer.fetch(DBCache.get(workspaceId.toString()), component.componentDataStoreId!!)
                                    .toDto()
                            acc.add(componentDto)
                        } catch (ex: NoSuchElementException) {
                            logger.warn("The data store ${component.componentDataStoreId} of ${component.componentType} workspace component ${component.id} does not exist")
                        }
                    } else {
                        acc.add(componentDto)
                    }

                    return@fold acc
                }.toList()
        }
    }

    /**
     * Adds or updates the specified [workspaceComponentId]. If particular parameter: [name], [componentType], [customizationData] is not specified, then it's not added/updated.
     * Throws [ValidationException] if the specified [userId] has insufficient permissions.
     */
    fun addOrUpdateWorkspaceComponent(
        workspaceComponentId: UUID,
        workspaceId: UUID,
        userId: UUID,
        organizationId: UUID,
        name: String?,
        query: String?,
        componentType: ComponentTypeDto?,
        customizationData: String? = null,
        layoutData: String? = null
    ): Unit = transaction(DBCache.getMainDBPool().database) {
        val canWorkspaceBeModified = UsersInGroups
            .innerJoin(UserGroups)
            .innerJoin(UserGroupWithWorkspaces)
            .innerJoin(Workspaces)
            .select {
                UserGroupWithWorkspaces.workspaceId eq workspaceId and
                        (UserGroupWithWorkspaces.organizationId eq organizationId) and
                        (UsersInGroups.userId eq userId) and
                        (UserGroups.groupRoleId neq GroupRoles.getIdByName(GroupRoleDto.Reader))
            }
            .limit(1)
            .any()

        if (!canWorkspaceBeModified) {
            throw ValidationException(
                ValidationException.Reason.ResourceNotFound,
                "The specified workspace/component does not exist or the user has insufficient permissions to it"
            )
        }

        val componentAlreadyExists = WorkspaceComponents
            .select {WorkspaceComponents.id eq workspaceComponentId}
            .limit(1).any()

        if (componentAlreadyExists) {
            return@transaction updateComponent(workspaceComponentId, workspaceId, name, query, componentType, customizationData, layoutData)
        }

        if (name == null || query == null || componentType == null) {
            throw ValidationException(
                ValidationException.Reason.ResourceFormatInvalid,
                "The specified workspace component does not exists and cannot be created"
            )
        }

        addComponent(workspaceComponentId, workspaceId, name, query, componentType, customizationData, layoutData)
    }

    /**
     * Removes the specified [workspaceComponentId].s
     * Throws [ValidationException] if the specified [userId] has insufficient permissions or [workspaceComponentId] doesn't exist.
     */
    fun removeWorkspaceComponent(
        workspaceComponentId: UUID,
        workspaceId: UUID,
        userId: UUID,
        organizationId: UUID,
    ) = transaction(DBCache.getMainDBPool().database) {
        val canWorkspaceBeRemoved = UsersInGroups
            .innerJoin(UserGroups)
            .innerJoin(UserGroupWithWorkspaces)
            .innerJoin(Workspaces)
            .innerJoin(WorkspaceComponents)
            .select {
                UserGroupWithWorkspaces.workspaceId eq workspaceId and
                        (WorkspaceComponents.id eq workspaceComponentId) and
                        (UserGroupWithWorkspaces.organizationId eq organizationId) and
                        (UsersInGroups.userId eq userId) and
                        (UserGroups.groupRoleId neq GroupRoles.getIdByName(GroupRoleDto.Reader))
            }
            .limit(1)
            .any()

        if (!canWorkspaceBeRemoved) {
            throw ValidationException(
                ValidationException.Reason.ResourceNotFound,
                "The specified workspace/component does not exist or the user has insufficient permissions to it"
            )
        }

        WorkspaceComponents.deleteWhere {
            WorkspaceComponents.id eq workspaceComponentId
        } > 0
    }

    /**
     * Update layout information related to the specified components inside [workspaceId].
     * Throws [ValidationException] if the specified [userId] has insufficient permissions or a component doesn't exist.
     */
    fun updateWorkspaceLayout(
        workspaceId: UUID,
        userId: UUID,
        organizationId: UUID,
        layout: Map<UUID, String>
    ): Unit = transaction(DBCache.getMainDBPool().database) {
        val canBeUpdated = UsersInGroups
            .innerJoin(UserGroups)
            .innerJoin(UserGroupWithWorkspaces)
            .innerJoin(Workspaces)
            .innerJoin(WorkspaceComponents)
            .select {
                WorkspaceComponents.id inList layout.keys and
                        (UserGroupWithWorkspaces.workspaceId eq workspaceId) and
                        (UserGroupWithWorkspaces.organizationId eq organizationId) and
                        (UsersInGroups.userId eq userId) and (UserGroups.groupRoleId neq GroupRoles.getIdByName(GroupRoleDto.Reader))
            }
            .count() == layout.size.toLong()

        if (!canBeUpdated) {
            throw ValidationException(
                ValidationException.Reason.ResourceNotFound,
                "The specified workspace does not exist or the user has insufficient permissions to it"
            )
        }

        BatchUpdateStatement(WorkspaceComponents).apply {
            layout.forEach { (componentId, layoutData) ->
                addBatch(EntityID(componentId, WorkspaceComponents))
                this[WorkspaceComponents.layoutData] = layoutData
            }
        }.execute(this)
    }

    private fun addComponent(
        workspaceComponentId: UUID,
        workspaceId: UUID,
        name: String,
        query: String,
        componentType: ComponentTypeDto,
        customizationData: String? = null,
        layoutData: String? = null
    ) {
        WorkspaceComponents.insert {
            it[WorkspaceComponents.id] = EntityID(workspaceComponentId, WorkspaceComponents)
            it[WorkspaceComponents.name] = name
            it[WorkspaceComponents.query] = query
            it[WorkspaceComponents.componentType] = componentType.typeName
            it[WorkspaceComponents.customizationData] = customizationData
            it[WorkspaceComponents.layoutData] = layoutData
            it[WorkspaceComponents.workspaceId] = EntityID(workspaceId, Workspaces)
        }
    }

    private fun updateComponent(
        workspaceComponentId: UUID,
        workspaceId: UUID?,
        name: String?,
        query: String?,
        componentType: ComponentTypeDto?,
        customizationData: String? = null,
        layoutData: String? = null
    ) {
        WorkspaceComponents.update({ WorkspaceComponents.id eq workspaceComponentId }) {
            if (workspaceId != null) it[WorkspaceComponents.workspaceId] = EntityID(workspaceId, Workspaces)
            if (name != null) it[WorkspaceComponents.name] = name
            if (query != null) it[WorkspaceComponents.query] = query
            if (componentType != null) it[WorkspaceComponents.componentType] = componentType.typeName
            if (customizationData != null) it[WorkspaceComponents.customizationData] = customizationData
            if (layoutData != null) it[WorkspaceComponents.layoutData] = layoutData
        }
    }

    private fun MutableCausalNet.toDto(): CausalNetDto {
        val nodes =
            (sequenceOf(start) +
                    activities.filter { it != start && it != end } +
                    sequenceOf(end))
                .map {
                    CausalNetNodeDto(
                        it.name,
                        splits[it].orEmpty().mapToArray { split -> split.targets.mapToArray { t -> t.name } },
                        joins[it].orEmpty().mapToArray { join -> join.sources.mapToArray { s -> s.name } }
                    )
                }
                .toList()
        val edges = dependencies.map {
            CausalNetEdgeDto(
                it.source.name,
                it.target.name
            )
        }

        return CausalNetDto(nodes, edges)
    }
}