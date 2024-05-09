package processm.services.logic

import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.sql.JoinType
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.statements.BatchUpdateStatement
import org.jetbrains.exposed.sql.stringLiteral
import processm.core.communication.Producer
import processm.core.persistence.connection.transactionMain
import processm.dbmodels.afterCommit
import processm.dbmodels.models.*
import processm.dbmodels.urn
import processm.logging.loggedScope
import processm.services.api.getCustomProperties
import processm.services.api.models.AbstractComponent
import processm.services.api.models.CustomProperty
import processm.services.api.toComponentType
import processm.services.api.updateData
import processm.services.helpers.ExceptionReason
import java.time.Instant
import java.util.*

fun Array<CustomProperty>.toMap() = fold(HashMap<String, String>()) { target, item ->
    item.value?.let { target[item.name] = item.value }
    return@fold target
}

class WorkspaceService(
    private val accountService: AccountService,
    private val aclService: ACLService,
    private val producer: Producer
) {
    /**
     * Returns all user workspaces for the specified [userId]
     */
    fun getUserWorkspaces(userId: UUID): List<Workspace> =
        transactionMain {
            Workspace.wrapRows(
                Groups
                    .innerJoin(UsersInGroups)
                    .crossJoin(Workspaces)
                    .join(AccessControlList, JoinType.INNER, AccessControlList.group_id, Groups.id)
                    .slice(Workspaces.columns)
                    .select {
                        (UsersInGroups.userId eq userId) and
                                (AccessControlList.urn.column eq concat(
                                    stringLiteral("urn:processm:db/${Workspaces.tableName}/"),
                                    Workspaces.id
                                )) and
                                (AccessControlList.role_id neq RoleType.None.role.id) and
                                (Workspaces.deleted eq false)
                    }.withDistinct(true)
            ).toList()

        }

    /**
     * Creates new workspace with the given [name] within the given [organizationId] and assigns it to private group of
     * the specified [userId].
     */
    fun create(name: String, userId: UUID, organizationId: UUID): UUID =
        transactionMain {
            name.isNotBlank().validate(ExceptionReason.WorkspaceNameRequired)

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

    fun update(workspaceId: UUID, newName: String) =
        transactionMain {

            with(Workspace[workspaceId]) {
                name = newName
            }
        }

    /**
     * Removes the specified [workspaceId].
     */
    fun remove(workspaceId: UUID): Unit = loggedScope { logger ->
        transactionMain {

            Workspace.findById(workspaceId)
                .validateNotNull(ExceptionReason.WorkspaceNotFound, workspaceId)
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
     * Returns the specified component [componentId].
     */
    fun getComponent(componentId: UUID): WorkspaceComponent =
        transactionMain {
            WorkspaceComponent.find {
                (WorkspaceComponents.id eq componentId) and (WorkspaceComponents.deleted eq false)
            }.single()
        }

    /**
     * Returns all components in the specified [workspaceId].
     */
    fun getComponents(workspaceId: UUID): List<WorkspaceComponent> =
        transactionMain {
            WorkspaceComponent.find {
                (WorkspaceComponents.workspaceId eq workspaceId) and (WorkspaceComponents.deleted eq false)
            }.toList()
        }

    /**
     * Adds or updates the specified [workspaceComponentId]. If particular parameter: [name], [componentType], [customizationData] is not specified, then it's not added/updated.
     */
    fun addOrUpdateComponent(
        workspaceComponentId: UUID,
        workspaceId: UUID,
        name: String?,
        query: String?,
        dataStore: UUID?,
        componentType: ComponentTypeDto?,
        customizationData: String? = null,
        layoutData: String? = null,
        data: String? = null,
        customProperties: Array<CustomProperty>
    ): Unit = transactionMain {
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
                customizationData,
                layoutData,
                data,
                customProperties
            )
        }

        name.isNullOrBlank().validateNot(ExceptionReason.BlankName)
        query.isNullOrBlank().validateNot(ExceptionReason.QueryRequired)

        // data is ignored here on purpose under the assumption that a new component's data is populated server-side
        addComponent(
            workspaceComponentId,
            workspaceId,
            name!!,
            query!!,
            dataStore.validateNotNull(ExceptionReason.DataStoreRequired),
            componentType.validateNotNull(ExceptionReason.ComponentTypeRequired),
            customizationData,
            layoutData,
            customProperties
        )
    }

    /**
     * Removes the specified [workspaceComponentId].s
     * Throws [ValidationException] if the specified [workspaceComponentId] doesn't exist.
     */
    fun removeComponent(
        workspaceComponentId: UUID,
    ): Unit = transactionMain {
        WorkspaceComponent.findById(workspaceComponentId)
            .validateNotNull(ExceptionReason.WorkspaceComponentNotFound)
            .apply { triggerEvent(producer, DELETE) }
            .deleted = true
    }

    /**
     * Update layout information related to the specified components.
     * Throws [ValidationException] if a component doesn't exist.
     */
    fun updateLayout(
        layout: Map<UUID, String>
    ): Unit = transactionMain {
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
            this.properties = customProperties.toMap()
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
        customizationData: String? = null,
        layoutData: String? = null,
        data: String? = null,
        customProperties: Array<CustomProperty> = emptyArray()
    ) {
        WorkspaceComponent[workspaceComponentId].apply {
            var trigger = false
            if (workspaceId != null) this.workspace = Workspace[workspaceId]
            if (name != null) this.name = name
            if (query != null && this.query != query) {
                this.query = query
                trigger = true
            }
            if (dataStore != null && this.dataStoreId != dataStore) {
                this.dataStoreId = dataStore
                trigger = true
            }
            // updating componentType is not supported, as it is impossible to convert data from one component type to another
            if (customizationData != null) this.customizationData = customizationData
            if (layoutData != null) this.layoutData = layoutData
            if (data != null) this.updateData(data)
            val newCustomProperties = customProperties.toMap()
            if (properties != newCustomProperties) {
                this.properties = newCustomProperties
                trigger = true
            }
            this.userLastModified = Instant.now()

            if (trigger) {
                afterCommit {
                    triggerEvent(producer)
                }
            }
        }
    }

    fun getEmptyComponent(type: ComponentTypeDto): AbstractComponent =
        AbstractComponent(
            UUID.randomUUID(),
            "",
            UUID.randomUUID(),
            type.toComponentType(),
            customProperties = getCustomProperties(type)
        )
}
