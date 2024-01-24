package processm.services.logic

import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.statements.BatchUpdateStatement
import processm.core.communication.Producer
import processm.core.logging.loggedScope
import processm.core.persistence.connection.transactionMain
import processm.dbmodels.afterCommit
import processm.dbmodels.models.*
import processm.dbmodels.urn
import processm.services.api.models.CustomProperty
import processm.services.api.updateData
import java.time.Instant
import java.util.*

class WorkspaceService(
    private val accountService: AccountService,
    private val aclService: ACLService,
    private val producer: Producer
) {
    /**
     * Returns all user workspaces for the specified [userId] in the context of the specified [organizationId].
     */
    fun getUserWorkspaces(userId: UUID, organizationId: UUID): List<Workspace> =
        transactionMain {
            Workspace.wrapRows(
                Workspaces.select {
                    Workspaces.id inSubQuery Groups
                        .innerJoin(UsersInGroups)
                        .crossJoin(Workspaces)
                        .join(AccessControlList, JoinType.INNER, AccessControlList.group_id, Groups.id)
                        .slice(Workspaces.id)
                        .select {
                            (UsersInGroups.userId eq userId) and
                                    ((Groups.isImplicit eq true) or (Groups.organizationId eq organizationId)) and
                                    (AccessControlList.urn.column eq concat(
                                        stringLiteral("urn:processm:db/${Workspaces.tableName}/"),
                                        Workspaces.id
                                    )) and
                                    (AccessControlList.role_id neq RoleType.None.role.id) and
                                    (Workspaces.deleted eq false)
                        }
                }.withDistinct(true)
            ).toList()
        }

    /**
     * Creates new workspace with the given [name] within the given [organizationId] and assigns it to private group of
     * the specified [userId].
     */
    fun create(name: String, userId: UUID, organizationId: UUID): UUID =
        transactionMain {
            name.isNotBlank().validate(Reason.ResourceFormatInvalid) { "Workspace name must not be blank." }

            val user = accountService.getUser(userId)
            val sharedGroup = Group.find {
                (Groups.isShared eq true) and (Groups.organizationId eq organizationId)
            }.first()

            val workspace = Workspace.new {
                this.name = name
            }

            // Add ACL entry for the user being the owner
            aclService.addEntry(workspace.urn, user.privateGroup.id.value, RoleType.Owner)

            // Add ACL entry for the organization just to connect the workspace with the organization
            aclService.addEntry(workspace.urn, sharedGroup.id.value, RoleType.None)

            return@transactionMain workspace.id.value
        }

    fun update(userId: UUID, organizationId: UUID, workspace: ApiWorkspace) =
        transactionMain {
            workspace.id.validateNotNull { "Workspace id must not be null." }
            aclService.checkAccess(userId, organizationId, Workspaces, workspace.id, RoleType.Writer)

            with(Workspace[workspace.id]) {
                name = workspace.name
            }
        }

    /**
     * Removes the specified [workspaceId].
     */
    fun remove(workspaceId: UUID, userId: UUID, organizationId: UUID): Unit = loggedScope { logger ->
        transactionMain {
            aclService.checkAccess(userId, organizationId, Workspaces, workspaceId, RoleType.Owner)

            Workspace.findById(workspaceId)
                .validateNotNull(Reason.ResourceNotFound) { "Workspace $workspaceId is not found." }
                .deleted = true

            WorkspaceComponent.find {
                WorkspaceComponents.workspaceId eq workspaceId
            }.forEach { component ->
                component.deleted = true
                component.afterCommit {
                    component.triggerEvent(producer, DELETE)
                }
            }

            try {
                aclService.removeEntries(Workspaces, workspaceId)
            } catch (e: ValidationException) {
                logger.debug("Suppressed exception", e)
            }
        }
    }

    /**
     * Returns the specified component [componentId] from the workspace [workspaceId].
     */
    fun getComponent(componentId: UUID, userId: UUID, organizationId: UUID, workspaceId: UUID): WorkspaceComponent =
        transactionMain {
            aclService.checkAccess(userId, organizationId, Workspaces, workspaceId, RoleType.Reader)

            WorkspaceComponent.find {
                (WorkspaceComponents.id eq componentId) and (WorkspaceComponents.deleted eq false)
            }.single()
        }

    /**
     * Returns all components in the specified [workspaceId].
     */
    fun getComponents(workspaceId: UUID, userId: UUID, organizationId: UUID): List<WorkspaceComponent> =
        transactionMain {
            aclService.checkAccess(userId, organizationId, Workspaces, workspaceId, RoleType.Reader)

            WorkspaceComponent.find {
                (WorkspaceComponents.workspaceId eq workspaceId) and (WorkspaceComponents.deleted eq false)
            }.toList()
        }

    /**
     * Adds or updates the specified [workspaceComponentId]. If particular parameter: [name], [componentType], [customizationData] is not specified, then it's not added/updated.
     * Throws [ValidationException] if the specified [userId] has insufficient permissions.
     */
    fun addOrUpdateComponent(
        workspaceComponentId: UUID,
        workspaceId: UUID,
        userId: UUID,
        organizationId: UUID,
        name: String?,
        query: String?,
        dataStore: UUID?,
        componentType: ComponentTypeDto?,
        customizationData: String? = null,
        layoutData: String? = null,
        data: String? = null,
        customProperties: Array<CustomProperty>
    ): Unit = transactionMain {
        aclService.checkAccess(userId, organizationId, Workspaces, workspaceId, RoleType.Writer)

        val componentAlreadyExists = WorkspaceComponents
            .select { (WorkspaceComponents.id eq workspaceComponentId) and (WorkspaceComponents.deleted eq false) }
            .limit(1)
            .any()

        if (componentAlreadyExists) {
            return@transactionMain updateComponent(
                workspaceComponentId,
                workspaceId,
                name,
                query,
                dataStore,
                componentType,
                customizationData,
                layoutData,
                data,
                customProperties
            )
        }

        name.isNullOrBlank().validateNot { "Missing name." }
        query.isNullOrBlank().validateNot { "Missing query. " }
        dataStore.validateNotNull { "Missing data store. " }
        componentType.validateNotNull { "Missing component type. " }

        // data is ignored here on purpose under the assumption that a new component's data is populated server-side
        addComponent(
            workspaceComponentId,
            workspaceId,
            name!!,
            query!!,
            dataStore,
            componentType,
            customizationData,
            layoutData,
            customProperties
        )
    }

    /**
     * Removes the specified [workspaceComponentId].s
     * Throws [ValidationException] if the specified [userId] has insufficient permissions or [workspaceComponentId] doesn't exist.
     */
    fun removeComponent(
        workspaceComponentId: UUID,
        workspaceId: UUID,
        userId: UUID,
        organizationId: UUID,
    ): Unit = transactionMain {
        aclService.checkAccess(userId, organizationId, Workspaces, workspaceId, RoleType.Writer)

        WorkspaceComponent.findById(workspaceComponentId)
            .validateNotNull(Reason.ResourceNotFound) { "Workspace component is not found." }
            .apply { triggerEvent(producer, DELETE) }
            .deleted = true
    }

    /**
     * Update layout information related to the specified components inside [workspaceId].
     * Throws [ValidationException] if the specified [userId] has insufficient permissions or a component doesn't exist.
     */
    fun updateLayout(
        workspaceId: UUID,
        userId: UUID,
        organizationId: UUID,
        layout: Map<UUID, String>
    ): Unit = transactionMain {
        aclService.checkAccess(userId, organizationId, Workspaces, workspaceId, RoleType.Reader)

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
        layoutData: String? = null,
        customProperties: Array<CustomProperty> = emptyArray()
    ) {
        WorkspaceComponent.new(workspaceComponentId) {
            this.name = name
            this.query = query
            this.dataStoreId = dataStore
            this.componentType = ComponentTypeDto.byTypeNameInDatabase(componentType.typeName)
            this.customizationData = customizationData
            this.layoutData = layoutData
            this.workspace = Workspace[workspaceId]
            this.algorithm = customProperties.firstOrNull { it.name == "algorithm" }?.value
            this.userLastModified = Instant.now()

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
        layoutData: String? = null,
        data: String? = null,
        customProperties: Array<CustomProperty> = emptyArray()
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
            if (data != null) this.updateData(data)
            this.algorithm = customProperties.firstOrNull { it.name == "algorithm" }?.value
            this.userLastModified = Instant.now()

            afterCommit {
                triggerEvent(producer)
            }
        }
    }
}
