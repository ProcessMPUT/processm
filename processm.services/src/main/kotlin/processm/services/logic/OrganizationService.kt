package processm.services.logic

import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.transactions.transaction
import processm.core.logging.debug
import processm.core.logging.loggedScope
import processm.core.persistence.connection.DBCache
import processm.dbmodels.ilike
import processm.dbmodels.models.*
import processm.services.api.models.OrganizationRole
import java.util.*

class OrganizationService(
    private val accountService: AccountService,
    private val groupService: GroupService
) {
    /**
     * Returns all users explicitly assigned to the specified [organizationId].
     * Throws [ValidationException] if the specified [organizationId] doesn't exist.
     */
    fun getMember(organizationId: UUID) = transaction(DBCache.getMainDBPool().database) {
        // this returns only users explicitly assigned to the organization
        val organization = getOrganizationDao(organizationId)

        organization.userRoles.map { it.toDto() }
    }

    /**
     * Adds new member with the given [email] and [role] to the organization with [organizationId].
     * Creates a new account with random password if a user with the given email does not exist in the database.
     * @throws ValidationException if the user or the organization do not exist, or the user already belongs to the organization.
     */
    fun addMember(organizationId: UUID, email: String, role: OrganizationRole): User =
        loggedScope { logger ->
            transaction(DBCache.getMainDBPool().database) {
                val userWithEmail = User.find { Users.email ilike email }.firstOrNull()
                    ?: accountService.createUser(email, pass = UUID.randomUUID().toString())
                addMember(organizationId, userWithEmail.id.value, role)
            }
        }


    /**
     * Attaches user with [userId] to organization with [organizationId] and assigns this user with [role].
     * @throws ValidationException if the user or the organization do not exist, or the user already belongs to the organization.
     */
    fun addMember(organizationId: UUID, userId: UUID, role: OrganizationRole): User = loggedScope { logger ->
        transaction(DBCache.getMainDBPool().database) {
            val organization = Organization.findById(organizationId) ?: throw ValidationException(
                ValidationException.Reason.ResourceNotFound,
                "Organization does not exist."
            )

            val user = User.findById(userId) ?: throw ValidationException(
                ValidationException.Reason.ResourceNotFound,
                "User does not exists."
            )

            val urio = UserRoleInOrganization.find {
                (UsersRolesInOrganizations.userId eq user.id) and (UsersRolesInOrganizations.organizationId eq organization.id)
            }.firstOrNull()
            if (urio !== null) {
                throw ValidationException(
                    ValidationException.Reason.ResourceAlreadyExists,
                    "User already exists in the organization."
                )
            }

            UserRoleInOrganization.new {
                this.user = user
                this.organization = organization
                this.role = role.toDB()
            }

            // add user to the group of the organization
            groupService.attachUserToGroup(userId, organization.sharedGroup.id.value)

            logger.debug("Added user ${user.id} to organization ${organization.id}.")

            user
        }
    }

    fun removeMember(organizationId: UUID, userId: UUID) = loggedScope { logger ->
        transaction(DBCache.getMainDBPool().database) {
            val organization = Organization.findById(organizationId) ?: throw ValidationException(
                ValidationException.Reason.ResourceNotFound,
                "Organization does not exist."
            )

            val user = User.findById(userId) ?: throw ValidationException(
                ValidationException.Reason.ResourceNotFound,
                "User does not exists."
            )

            val deleted = UsersRolesInOrganizations.deleteWhere {
                (UsersRolesInOrganizations.userId eq userId) and (UsersRolesInOrganizations.organizationId eq organizationId)
            }
            groupService.detachUserFromGroup(userId, organization.sharedGroup.id.value)

            if (deleted == 0) {
                throw ValidationException(
                    ValidationException.Reason.ResourceNotFound,
                    "User $userId is not found in organizations $organizationId."
                )
            }
        }
    }

    /**
     * Returns all user groups explicitly assigned to the specified [organizationId].
     * Throws [ValidationException] if the specified [organizationId] doesn't exist.
     */
    fun getOrganizationGroups(organizationId: UUID) = transaction(DBCache.getMainDBPool().database) {
        val organization = getOrganizationDao(organizationId)

        return@transaction listOf(organization.sharedGroup.toDto())
    }

    /**
     * Returns organization by the specified [sharedGroupId].
     * Throws [ValidationException] if the organization doesn't exist.
     */
    fun getOrganizationBySharedGroupId(sharedGroupId: UUID) = transaction(DBCache.getMainDBPool().database) {
        val organization = Organizations.select { Organizations.sharedGroupId eq sharedGroupId }.firstOrNull()
            ?: throw ValidationException(
                ValidationException.Reason.ResourceNotFound,
                "The specified shared group id is not assigned to any organization"
            )

        return@transaction Organization.wrapRow(organization).toDto()
    }

    /**
     * Creates a new organization with the given [name]. The organization name may be not unique.
     */
    fun createOrganization(name: String, isPrivate: Boolean, parent: UUID? = null): Organization =
        loggedScope { logger ->
            transaction(DBCache.getMainDBPool().database) {
                // automatically created group for all users
                val sharedGroup = UserGroup.new {
                    this.groupRole = GroupRoleType.Reader.groupRole
                    this.isImplicit = true
                }

                val org = Organization.new {
                    this.name = name
                    this.isPrivate = isPrivate
                    this.parentOrganization = parent?.let { Organization[it] }
                    this.sharedGroup = sharedGroup
                }

                logger.debug { "Organization $name with id ${org.id.value} is created" }

                org
            }
        }

    private fun getOrganizationDao(organizationId: UUID) = transaction(DBCache.getMainDBPool().database) {
        Organization.findById(organizationId) ?: throw ValidationException(
            ValidationException.Reason.ResourceNotFound, "The specified organization does not exist"
        )
    }
}
