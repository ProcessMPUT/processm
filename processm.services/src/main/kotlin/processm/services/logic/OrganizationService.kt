package processm.services.logic

import org.jetbrains.exposed.dao.load
import org.jetbrains.exposed.dao.with
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.inSubQuery
import processm.core.models.metadata.URN
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

    fun getSoleOwnershipURNs(organizationId: UUID): List<URN> = transactionMain {
        val owner = RoleType.Owner.role.id
        val acl1 = AccessControlList.alias("acl1")
        val g1 = Groups.alias("g1")
        val acl2 = AccessControlList.alias("acl2")
        val g2 = Groups.alias("g2")
        acl1
            .join(g1, JoinType.INNER, acl1[AccessControlList.group_id], g1[Groups.id])
            .slice(acl1[AccessControlList.urn.column])
            .select {
                (acl1[AccessControlList.role_id] eq owner) and
                        (g1[Groups.organizationId] eq organizationId) and
                        notExists(acl2
                            .join(g2, JoinType.INNER, acl2[AccessControlList.group_id], g2[Groups.id])
                            .select {
                                (g2[Groups.organizationId] neq organizationId or g2[Groups.organizationId].isNull()) and
                                        (acl2[AccessControlList.role_id] eq owner) and
                                        (acl1[AccessControlList.urn.column] eq acl2[AccessControlList.urn.column])
                            })
            }
            .withDistinct()
            .map { URN(it[acl1[AccessControlList.urn.column]]) }

    }

    /**
     * Deletes organization with the given [id]. All child organization become independent,
     * all ACL entries referring to the groups of the organization are removed, and the groups are removed as well.
     *
     * @throws ValidationException if there are objects that would lose all their owners once the organization is removed,
     * i.e., if [getSoleOwnershipURNs] returns a non-empty list.
     */
    fun remove(id: UUID): Unit = transactionMain {
        getSoleOwnershipURNs(id).isEmpty().validate(Reason.UnprocessableResource)
        Organizations.update(where = { Organizations.parentOrganizationId eq id }) {
            it[parentOrganizationId] = null
        }
        AccessControlList.deleteWhere {
            group_id inSubQuery Groups.slice(Groups.id).select { Groups.organizationId eq id }
        }
        Groups.deleteWhere { organizationId eq id }
        Organizations.deleteWhere {
            Organizations.id eq id
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
        result.forEach { it.load(Organization::parentOrganization) }
        result.toList()
    }

    private fun Transaction.getOrganization(organizationId: UUID): Organization =
        Organization
            .findById(organizationId)
            .validateNotNull(Reason.ResourceNotFound, "The organization $organizationId does not exist.")
            .load(Organization::parentOrganization)

    fun attachSubOrganization(organizationId: UUID, subOrganizationId: UUID) {
        transactionMain {
            organizationId.validateNot(subOrganizationId) { "The organization cannot be attached as its own child." }
            val organization = Organization.findById(organizationId)
                .validateNotNull(Reason.ResourceNotFound, "The organization $organizationId does not exist.")
            val subOrganization = Organization.findById(subOrganizationId)
                .validateNotNull(Reason.ResourceNotFound, "The organization $subOrganizationId does not exist.")
            subOrganization.parentOrganization.validateNull { "The organization $subOrganizationId already has a parent organization" }
            val expr = OrganizationsDescendants.subOrganizationId.count()
            OrganizationsDescendants
                .slice(expr)
                .select {
                    (OrganizationsDescendants.subOrganizationId eq organizationId) and (OrganizationsDescendants.superOrganizationId eq subOrganizationId)
                }.firstNotNullOf { row -> row[expr] }
                .validate(0) { "The organization $organizationId is already a descendant of $subOrganizationId" }
            subOrganization.parentOrganization = organization
        }
    }

    fun detachSubOrganization(organizationId: UUID) {
        transactionMain {
            val organization = Organization.findById(organizationId)
                .validateNotNull(Reason.ResourceNotFound, "The organization $organizationId does not exist.")
            organization.parentOrganization.validateNotNull { "The organization $organizationId is already a top-level organization" }
            organization.parentOrganization = null
        }
    }

    fun getSubOrganizations(organizationId: UUID): List<Organization> = transactionMain {
        return@transactionMain Organization.wrapRows(OrganizationsDescendants
            .join(Organizations, JoinType.INNER, OrganizationsDescendants.subOrganizationId, Organizations.id)
            .select { OrganizationsDescendants.superOrganizationId eq organizationId }).toList()
    }
}
