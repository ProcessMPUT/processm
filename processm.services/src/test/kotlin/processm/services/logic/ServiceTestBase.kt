package processm.services.logic

import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.Transaction
import org.jetbrains.exposed.sql.deleteAll
import org.jetbrains.exposed.sql.insertAndGetId
import org.jetbrains.exposed.sql.transactions.transaction
import org.junit.jupiter.api.BeforeAll
import processm.core.persistence.DBConnectionPool
import processm.services.models.*
import java.util.*

abstract class ServiceTestBase {

    @BeforeAll
    protected fun setUpAll() {
        // create initial connection to enforce execution of db migrations
        DBConnectionPool.database
    }

    protected fun <R> withCleanTables(vararg tables: Table, testLogic: Transaction.() -> R) =
        transaction(DBConnectionPool.database) {
            tables.forEach { it.deleteAll() }
            testLogic(this)
        }

    protected fun Transaction.createUser(userEmail: String = "user@example.com", passwordHash: String = "###", locale: String = "en_US") =
        Users.insertAndGetId {
            it[email] = userEmail
            it[password] = passwordHash
            it[Users.locale] = locale
        }

    protected fun Transaction.createOrganization(name: String = "Org1", isPrivate: Boolean = false, parentOrganizationId: UUID? = null) =
        Organizations.insertAndGetId {
            it[this.name] = name
            it[this.isPrivate] = isPrivate
            it[this.parentOrganizationId] = if (parentOrganizationId != null) EntityID(parentOrganizationId, Organizations) else null
        }

    protected fun Transaction.attachUserToOrganization(userId: UUID, organizationId: UUID, organizationRole: OrganizationRoleDto = OrganizationRoleDto.Reader)  =
        UsersRolesInOrganizations.insertAndGetId {
            it[UsersRolesInOrganizations.userId] = EntityID(userId, Users)
            it[UsersRolesInOrganizations.organizationId] = EntityID(organizationId, Organizations)
            it[roleId] = OrganizationRoles.getIdByName(organizationRole)
        }

    protected fun Transaction.createGroup(organizationId: UUID, name: String = "Group1", parentGroupId: UUID? = null, groupRole: GroupRoleDto = GroupRoleDto.Reader, isImplicit: Boolean = false) =
        UserGroups.insertAndGetId {
            it[this.name] = name
            it[this.parentGroupId] = if (parentGroupId != null) EntityID(parentGroupId, UserGroups) else null
            it[this.groupRoleId] = GroupRoles.getIdByName(groupRole)
            it[this.isImplicit] = isImplicit
        }
}
