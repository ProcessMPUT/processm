package processm.services.logic

import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insertAndGetId
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.statements.BatchUpdateStatement
import org.jetbrains.exposed.sql.transactions.transaction
import processm.core.communication.Producer
import processm.core.persistence.connection.DBCache
import processm.dbmodels.afterCommit
import processm.dbmodels.models.*
import processm.miners.triggerEvent
import processm.services.api.models.GroupRole
import java.util.*

class WorkspaceService(private val accountService: AccountService, private val producer: Producer) {
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

    fun updateWorkspace(userId: UUID, organizationId: UUID, workspace: WorkspaceDto) =
        transaction(DBCache.getMainDBPool().database) {
            hasPermissionToEdit(workspace.id, organizationId, userId)

            with(Workspace[workspace.id]) {
                name = workspace.name
            }
        }

    /**
     * Removes the specified [workspaceId].
     * Throws [ValidationException] if the specified [userId] has insufficient permissions or the [workspaceId] doesn't exist.
     */
    fun removeWorkspace(workspaceId: UUID, userId: UUID, organizationId: UUID) =
        transaction(DBCache.getMainDBPool().database) {
            hasPermissionToEdit(workspaceId, organizationId, userId)

            Workspaces.deleteWhere {
                Workspaces.id eq workspaceId
            } > 0
        }

    private fun hasPermissionToEdit(
        workspaceId: UUID,
        organizationId: UUID,
        userId: UUID
    ) {
        val hasPermission = UsersInGroups
            .innerJoin(UserGroups)
            .innerJoin(UserGroupWithWorkspaces)
            .select {
                UserGroupWithWorkspaces.workspaceId eq workspaceId and
                        (UserGroupWithWorkspaces.organizationId eq organizationId) and
                        (UsersInGroups.userId eq userId) and
                        (UserGroups.groupRoleId neq GroupRole.reader.toDB().id)
            }
            .limit(1)
            .any()

        if (!hasPermission) {
            throw ValidationException(
                ValidationException.Reason.ResourceNotFound,
                "The specified workspace does not exist or the user has insufficient permissions to use it."
            )
        }
    }

    /**
     * Returns all components in the specified [workspaceId].
     */
    fun getWorkspaceComponents(workspaceId: UUID, userId: UUID, organizationId: UUID): List<WorkspaceComponent> =
        transaction(DBCache.getMainDBPool().database) {
            WorkspaceComponent.wrapRows(
                WorkspaceComponents
                    .innerJoin(Workspaces)
                    .innerJoin(UserGroupWithWorkspaces)
                    .innerJoin(UserGroups)
                    .innerJoin(UsersInGroups)
                    .select(WorkspaceComponents.workspaceId eq workspaceId and (UserGroupWithWorkspaces.organizationId eq organizationId) and (UsersInGroups.userId eq userId))
            ).toList()
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
        dataStore: UUID?,
        componentType: ComponentTypeDto?,
        customizationData: String? = null,
        layoutData: String? = null
    ): Unit = transaction(DBCache.getMainDBPool().database) {
        hasPermissionToEdit(workspaceId, organizationId, userId)

        val componentAlreadyExists = WorkspaceComponents
            .select { WorkspaceComponents.id eq workspaceComponentId }
            .limit(1).any()

        if (componentAlreadyExists) {
            return@transaction updateComponent(
                workspaceComponentId,
                workspaceId,
                name,
                query,
                dataStore,
                componentType,
                customizationData,
                layoutData
            )
        }

        if (name.isNullOrBlank())
            throw ValidationException(ValidationException.Reason.ResourceFormatInvalid, "Missing name.")

        if (query.isNullOrBlank())
            throw ValidationException(ValidationException.Reason.ResourceFormatInvalid, "Missing query.")

        if (dataStore == null)
            throw ValidationException(ValidationException.Reason.ResourceFormatInvalid, "Missing data store.")

        if (componentType == null)
            throw ValidationException(ValidationException.Reason.ResourceFormatInvalid, "Missing component type.")

        addComponent(
            workspaceComponentId,
            workspaceId,
            name,
            query,
            dataStore,
            componentType,
            customizationData,
            layoutData
        )
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
        hasPermissionToEdit(workspaceId, organizationId, userId)

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
                        (UsersInGroups.userId eq userId) and
                        (UserGroups.groupRoleId neq GroupRole.reader.toDB().id)
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
        dataStore: UUID,
        componentType: ComponentTypeDto,
        customizationData: String? = null,
        layoutData: String? = null
    ) {
        WorkspaceComponent.new(workspaceComponentId) {
            this.name = name
            this.query = query
            this.dataStoreId = dataStore
            this.componentType = ComponentTypeDto.byTypeNameInDatabase(componentType.typeName)
            this.customizationData = customizationData
            this.layoutData = layoutData
            this.workspace = Workspace[workspaceId]

            afterCommit {
                triggerEvent(producer)
            }
        }
    }

    private fun updateComponent(
        workspaceComponentId: UUID,
        workspaceId: UUID?,
        name: String?,
        query: String?,
        dataStore: UUID?,
        componentType: ComponentTypeDto?,
        customizationData: String? = null,
        layoutData: String? = null
    ) {
        WorkspaceComponent[workspaceComponentId].apply {
            if (workspaceId != null) this.workspace = Workspace[workspaceId]
            if (name != null) this.name = name
            if (query != null) this.query = query
            if (dataStore != null) this.dataStoreId = dataStore
            if (componentType != null) this.componentType =
                ComponentTypeDto.byTypeNameInDatabase(componentType.typeName)
            if (customizationData != null) this.customizationData = customizationData
            if (layoutData != null) this.layoutData = layoutData

            afterCommit {
                triggerEvent(producer)
            }
        }
    }
}
