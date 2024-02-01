package processm.services.logic

import io.mockk.MockKAnnotations
import io.mockk.every
import io.mockk.mockk
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.Transaction
import org.jetbrains.exposed.sql.deleteAll
import org.jetbrains.exposed.sql.insertAndGetId
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import processm.core.communication.Producer
import processm.core.communication.email.EMAIL_TOPIC
import processm.core.esb.Artemis
import processm.core.helpers.loadConfiguration
import processm.core.persistence.Migrator
import processm.core.persistence.connection.DatabaseChecker
import processm.core.persistence.connection.transactionMain
import processm.dbmodels.models.*
import java.sql.DriverManager
import java.time.LocalDateTime
import java.util.*
import kotlin.test.BeforeTest

abstract class ServiceTestBase {

    @BeforeTest
    open fun setUp() {
        MockKAnnotations.init(this, relaxUnitFun = true)
    }

    companion object {
        private lateinit var artemis: Artemis
        private val dbName = "processm-${UUID.randomUUID()}"

        @JvmStatic
        @BeforeAll
        fun `start Artemis`() {
            loadConfiguration(true)
            DriverManager.getConnection(DatabaseChecker.baseConnectionURL).use { connection ->
                connection.createStatement().use { stmt ->
                    stmt.execute("create database \"$dbName\"")
                }
            }
            System.setProperty(
                DatabaseChecker.databaseConnectionURLProperty,
                DatabaseChecker.baseConnectionURL.replace("/${DatabaseChecker.mainDatabaseName}", "/$dbName")
            )
            Migrator.reloadConfiguration()
            artemis = Artemis()
            artemis.register()
            artemis.start()
        }

        @JvmStatic
        @AfterAll
        fun `stop Artemis`() {
            artemis.stop()
            loadConfiguration(true)
            Migrator.reloadConfiguration()
            DriverManager.getConnection(DatabaseChecker.baseConnectionURL).use { connection ->
                connection.createStatement().use { stmt ->
                    stmt.execute("drop database \"$dbName\"")
                }
            }
        }
    }

    protected val producer: Producer = mockk {
        every { produce(DATA_CONNECTOR_TOPIC, any()) } returns Unit
        every { produce(EMAIL_TOPIC, any()) } returns Unit
    }

    protected val groupService = GroupService()
    protected val accountService = AccountService(groupService, producer)
    protected val organizationService = OrganizationService(accountService, groupService)
    protected val aclService = ACLService()
    protected val workspaceService = WorkspaceService(accountService, aclService, producer)

    protected fun <R> withCleanTables(vararg tables: Table, testLogic: Transaction.() -> R) =
        transactionMain {
            tables.forEach { it.deleteAll() }
            testLogic(this)
        }

    protected fun Transaction.createUser(
        userEmail: String = "user@example.com",
        password: String? = "P@ssw0rd!",
        passwordHash: String? = null,
        locale: String = "en_US",
        organizationId: UUID = UUID(12L, 12L)
    ): User {
        require(password === null || passwordHash === null)
        require(password !== null || passwordHash !== null)
        val user = accountService.create(userEmail, locale, password ?: passwordHash!!)
        if (passwordHash !== null) {
            // write hash directly to database, since create() takes password and hashes it internally
            accountService.update(user.id.value) {
                this.password = passwordHash
            }
        }
        // TODO: replace with OrganizationService call
        val org = Organization.findById(organizationId) ?: createOrganization()
        groupService.attachUserToGroup(user.id.value, org.sharedGroup.id.value)
        return user
    }

    protected fun createOrganization(
        name: String = "Org1",
        isPrivate: Boolean = false,
        parentOrganizationId: UUID? = null
    ): Organization = organizationService.create(name, isPrivate, parentOrganizationId)

    protected fun createDataStore(
        organizationId: UUID,
        name: String = "DataStore#1",
        creationDate: LocalDateTime = LocalDateTime.now()
    ): EntityID<UUID> {
        // TODO: replace with DataStoreService calls
        return DataStores.insertAndGetId {
            it[DataStores.name] = name
            it[DataStores.creationDate] = creationDate
            it[DataStores.organizationId] = EntityID(organizationId, Organizations)
        }
    }

    protected fun attachUserToOrganization(
        userId: UUID,
        organizationId: UUID,
        role: RoleType = RoleType.Reader
    ) = organizationService.addMember(organizationId, userId, role)

    protected fun Transaction.createGroup(
        name: String = "Group1",
        parentGroupId: UUID? = null,
        organizationId: UUID? = null,
        isImplicit: Boolean = (organizationId === null)
    ) = // TODO: replace with GroupService calls
        Groups.insertAndGetId {
            it[this.name] = name
            it[this.parentGroupId] = if (parentGroupId != null) EntityID(parentGroupId, Groups) else null
            it[this.organizationId] = organizationId
            it[this.isImplicit] = isImplicit
        }

    protected fun attachUserToGroup(userId: UUID, groupId: UUID) =
        groupService.attachUserToGroup(userId, groupId)

    protected fun createWorkspace(
        name: String = "Workspace1",
        userId: UUID,
        organizationId: UUID
    ) = workspaceService.create(name, userId, organizationId)

    protected fun Transaction.createWorkspaceComponent(
        name: String = "Component1",
        workspaceId: UUID,
        query: String = "SELECT ...",
        dataStore: UUID = UUID.randomUUID(),
        componentType: ComponentTypeDto = ComponentTypeDto.CausalNet,
        data: String? = null,
        customizationData: String = "{}"
    ): EntityID<UUID> {
        // TODO: replace with WorkspaceService calls
        return WorkspaceComponents.insertAndGetId {
            it[this.name] = name
            it[this.workspaceId] = EntityID(workspaceId, Workspaces)
            it[this.query] = query
            it[this.dataStoreId] = dataStore
            it[this.componentType] = componentType.typeName
            it[this.data] = data
            it[this.customizationData] = customizationData
        }
    }
}
