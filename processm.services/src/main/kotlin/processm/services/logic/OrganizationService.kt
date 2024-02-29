package processm.services.logic

import org.jetbrains.exposed.dao.with
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.inSubQuery
import processm.core.persistence.connection.transactionMain
import processm.dbmodels.ieq
import processm.dbmodels.models.*
import processm.logging.debug
import processm.logging.loggedScope
import java.util.*

class OrganizationService(
    private val accountService: AccountService,
    private val groupService: GroupService
) {
    /**
     * Returns all users explicitly assigned to the specified [organizationId].
     * Throws [ValidationException] if the specified [organizationId] doesn't exist.
     */
    fun getMembers(organizationId: UUID): List<UserRoleInOrganization> = transactionMain {
        // this returns only users explicitly assigned to the organization
        val organization = getOrganization(organizationId)

        organization.userRoles.toList()
    }

    /**
     * Adds new member with the given [email] and [role] to the organization with [organizationId].
     * Creates a new account with random password if a user with the given email does not exist in the database.
     * @throws ValidationException if the user or the organization do not exist, or the user already belongs to the organization.
     */
    fun addMember(organizationId: UUID, email: String, role: RoleType): User =
        loggedScope { logger ->
            transactionMain {
                assert(User.count(Users.email ieq email) in 0..1)
                val userWithEmail = User.find { Users.email ieq email }.firstOrNull()
                    ?: accountService.create(email, pass = "G3n3rAt3d!${UUID.randomUUID()}")
                addMember(organizationId, userWithEmail.id.value, role)
            }
        }


    /**
     * Attaches user with [userId] to organization with [organizationId] and assigns this user with [role].
     * @throws ValidationException if the user or the organization do not exist, or the user already belongs to the organization.
     */
    fun addMember(organizationId: UUID, userId: UUID, role: RoleType): User = loggedScope { logger ->
        transactionMain {
            val organization = Organization.findById(organizationId)
                .validateNotNull(Reason.ResourceNotFound, "Organization does not exist.")

            val user = User.findById(userId).validateNotNull(Reason.ResourceNotFound, "User does not exists.")

            UserRoleInOrganization.find {
                (UsersRolesInOrganizations.userId eq user.id) and (UsersRolesInOrganizations.organizationId eq organization.id)
            }.firstOrNull().validate(null, Reason.ResourceAlreadyExists, "User already exists in the organization.")

            UserRoleInOrganization.new {
                this.user = user
                this.organization = organization
                this.role = role.role
            }

            // add user to the group of the organization
            groupService.attachUserToGroup(userId, organization.sharedGroup.id.value)

            logger.debug("Added user ${user.id} to organization ${organization.id}.")

            user
        }
    }

    fun updateMember(organizationId: UUID, userId: UUID, role: RoleType): Unit =
        loggedScope { logger ->
            transactionMain {
                val member = UserRoleInOrganization.find {
                    (UsersRolesInOrganizations.organizationId eq organizationId) and (UsersRolesInOrganizations.userId eq userId)
                }.firstOrNull()
                    .validateNotNull(Reason.ResourceNotFound) { "User $userId is not found in organization $organizationId." }
                member.role = role.role

                logger.debug("Updated user $userId in organization $organizationId.")
            }
        }

    fun removeMember(organizationId: UUID, userId: UUID): Unit = loggedScope { logger ->
        transactionMain {
            val organization = Organization.findById(organizationId)
                .validateNotNull(Reason.ResourceNotFound, "Organization does not exist.")

            User.findById(userId).validateNotNull(Reason.ResourceNotFound, "User does not exists.")

            UsersRolesInOrganizations.deleteWhere {
                (UsersRolesInOrganizations.userId eq userId) and (UsersRolesInOrganizations.organizationId eq organizationId)
            }.validateNot(0, Reason.ResourceNotFound) { "User $userId is not found in organizations $organizationId." }

            // detach the user from all groups in this organization
            UsersInGroups.deleteWhere {
                (UsersInGroups.userId eq userId) and (UsersInGroups.groupId inSubQuery Groups.select { Groups.organizationId eq organizationId })
            }
            logger.debug { "Removed member $userId from organization $organizationId." }
        }
    }

    /**
     * Returns all user groups explicitly assigned to the specified [organizationId].
     * Throws [ValidationException] if the specified [organizationId] doesn't exist.
     */
    fun getOrganizationGroups(organizationId: UUID): List<Group> = transactionMain {
        val groups = Group
            .find { Groups.organizationId eq organizationId }
            .union(
                Group.wrapRows(
                    Groups.join(Users, JoinType.INNER, Groups.id, Users.privateGroupId)
                        .innerJoin(UsersRolesInOrganizations)
                        .select { UsersRolesInOrganizations.organizationId eq organizationId }
                )
            ).with(Group::organizationId /*eager loading*/).toList()

        // if organization exists, we should find at least the shared group
        groups.isNotEmpty().validate(Reason.ResourceNotFound, "Organization not found")

        groups
    }

    /**
     * Returns organization by the specified [sharedGroupId].
     * Throws [ValidationException] if the organization doesn't exist.
     */
    fun getOrganizationBySharedGroupId(sharedGroupId: UUID): Organization =
        transactionMain {
            val organization = Groups
                .innerJoin(Organizations)
                .select { (Groups.id eq sharedGroupId) and (Groups.isShared eq true) }
                .map { Organization.wrapRow(it) }
                .firstOrNull()
                .validateNotNull(
                    Reason.ResourceNotFound,
                    "The shared group $sharedGroupId is not assigned to any organization."
                )

            return@transactionMain organization
        }

    /**
     * Creates a new organization with the given [name]. The organization name may be not unique.
     */
    fun create(name: String, isPrivate: Boolean, parent: UUID? = null, ownerUserId: UUID? = null): Organization =
        loggedScope { logger ->
            name.isNotBlank().validate(Reason.ResourceFormatInvalid, "Name must not be blank or empty.")

            transactionMain {
                val org = Organization.new {
                    this.name = name
                    this.isPrivate = isPrivate
                    this.parentOrganization = parent?.let { Organization[it] }
                }

                // automatically created group for all users
                groupService.create(name, isShared = true, organizationId = org.id.value)

                if (ownerUserId !== null)
                    addMember(org.id.value, ownerUserId, RoleType.Owner)

                logger.debug { "Created organization $name with id ${org.id.value}" }

                org
            }
        }

    fun update(id: UUID, update: (Organization.() -> Unit)): Unit = transactionMain {
        getOrganization(id).update()
    }

    /**
     * Deletes organization with the given [id].
     */
    fun remove(id: UUID): Unit = transactionMain {
        Organizations.deleteWhere {
            Organizations.id eq id
        }

        Groups.deleteWhere {
            (Groups.organizationId eq id) and (Groups.isImplicit eq true)
        }
    }

    /**
     * Gets the organization with the given [organizationId].
     */
    fun get(organizationId: UUID): Organization = transactionMain {
        getOrganization(organizationId)
    }

    /**
     * Gets all organizations.
     */
    fun getAll(publicOnly: Boolean): List<Organization> = transactionMain {
        val result = if (!publicOnly) Organization.all()
        else Organization.find { Organizations.isPrivate eq false }
        result.toList()
    }

    private fun Transaction.getOrganization(organizationId: UUID): Organization =
        Organization
            .findById(organizationId)
            .validateNotNull(Reason.ResourceNotFound, "The organization $organizationId does not exist.")
}
