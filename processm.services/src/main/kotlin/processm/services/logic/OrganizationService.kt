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
import processm.services.helpers.ExceptionReason
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
                .validateNotNull(ExceptionReason.OrganizationNotFound)

            val user = User.findById(userId).validateNotNull(ExceptionReason.UserNotFound)

            UserRoleInOrganization.find {
                (UsersRolesInOrganizations.userId eq user.id) and (UsersRolesInOrganizations.organizationId eq organization.id)
            }.firstOrNull().validate(null, ExceptionReason.UserAlreadyInOrganization)

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
                    .validateNotNull(ExceptionReason.UserNotFoundInOrganization, userId, organizationId)
                member.role = role.role

                logger.debug("Updated user $userId in organization $organizationId.")
            }
        }

    fun removeMember(organizationId: UUID, userId: UUID): Unit = loggedScope { logger ->
        transactionMain {
            val organization = Organization.findById(organizationId)
                .validateNotNull(ExceptionReason.OrganizationNotFound)

            User.findById(userId).validateNotNull(ExceptionReason.UserNotFound)

            UsersRolesInOrganizations.deleteWhere {
                (UsersRolesInOrganizations.userId eq userId) and (UsersRolesInOrganizations.organizationId eq organizationId)
            }.validateNot(0, ExceptionReason.UserNotFoundInOrganization, userId, organizationId)

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
        groups.isNotEmpty().validate(ExceptionReason.OrganizationNotFound)

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
                .validateNotNull(ExceptionReason.SharedGroupNotAssigned, sharedGroupId)

            return@transactionMain organization
        }

    /**
     * Creates a new organization with the given [name]. The organization name may be not unique.
     */
    fun create(name: String, isPrivate: Boolean, parent: UUID? = null, ownerUserId: UUID? = null): Organization =
        loggedScope { logger ->
            name.isNotBlank().validate(ExceptionReason.BlankName)

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
        getSoleOwnershipURNs(id).isEmpty().validate(ExceptionReason.SoleOwner)
        val parentId = Organization.findById(id)?.parentOrganization?.id
        Organizations.update(where = { Organizations.parentOrganizationId eq id }) {
            it[parentOrganizationId] = parentId
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
     * Gets all organizations. All public organizations are returned. If [userId] is not `null`, all private organizations
     * the user identified by [userId] is a member of, are returned as well.
     */
    fun getAll(userId: UUID?): SizedIterable<Organization> = transactionMain {
        val condition = userId?.let { (UsersRolesInOrganizations.userId eq userId) } ?: booleanLiteral(false)
        Organization.wrapRows(
            Organizations
                .join(
                    UsersRolesInOrganizations,
                    JoinType.LEFT,
                    Organizations.id,
                    UsersRolesInOrganizations.organizationId
                )
                .slice(Organizations.columns)
                .select { (Organizations.isPrivate eq false) or condition }
                .withDistinct()
        ).onEach { it.load(Organization::parentOrganization) }
    }

    private fun Transaction.getOrganization(organizationId: UUID): Organization =
        Organization
            .findById(organizationId)
            .validateNotNull(ExceptionReason.OrganizationNotFound, organizationId)
            .load(Organization::parentOrganization)

    fun attachSubOrganization(organizationId: UUID, subOrganizationId: UUID) {
        transactionMain {
            organizationId.validateNot(subOrganizationId, ExceptionReason.OrganizationCannotBeItsOwnChild)
            val organization = Organization.findById(organizationId)
                .validateNotNull(ExceptionReason.OrganizationNotFound, organizationId)
            val subOrganization = Organization.findById(subOrganizationId)
                .validateNotNull(ExceptionReason.OrganizationNotFound, subOrganizationId)
            subOrganization.parentOrganization.validateNull(
                ExceptionReason.ParentOrganizationAlreadySet,
                subOrganizationId
            )
            val expr = OrganizationsDescendants.subOrganizationId.count()
            OrganizationsDescendants
                .slice(expr)
                .select {
                    (OrganizationsDescendants.subOrganizationId eq organizationId) and (OrganizationsDescendants.superOrganizationId eq subOrganizationId)
                }.firstNotNullOf { row -> row[expr] }
                .validate(0, ExceptionReason.AlreadyDescendant, organizationId, subOrganizationId)
            subOrganization.parentOrganization = organization
        }
    }

    fun detachSubOrganization(organizationId: UUID) {
        transactionMain {
            val organization = Organization.findById(organizationId)
                .validateNotNull(ExceptionReason.OrganizationNotFound, organizationId)
            organization.parentOrganization.validateNotNull(
                ExceptionReason.OrganizationAlreadyTopLevel,
                organizationId
            )
            organization.parentOrganization = null
        }
    }

    fun getSubOrganizations(organizationId: UUID): List<Organization> = transactionMain {
        return@transactionMain Organization.wrapRows(OrganizationsDescendants
            .join(Organizations, JoinType.INNER, OrganizationsDescendants.subOrganizationId, Organizations.id)
            .select { OrganizationsDescendants.superOrganizationId eq organizationId }).toList()
    }
}
