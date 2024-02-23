package processm.services.logic

import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import processm.core.models.metadata.URN
import processm.core.persistence.connection.transactionMain
import processm.dbmodels.models.*
import java.util.*

class ACLService {

    /**
     * Creates a DB query that selects the ACL corresponding to [userId] and [role].
     */
    fun queryUserACL(userId: UUID, role: RoleType): Query {
        val allowedRoles = RoleType.values().filter { it.ordinal <= role.ordinal }.map { it.role.id }

        return Groups.innerJoin(UsersInGroups)
            .join(AccessControlList, JoinType.INNER, AccessControlList.group_id, Groups.id)
            .select {
                (UsersInGroups.userId eq userId) and
                        (AccessControlList.role_id inList allowedRoles)
            }
    }

    /**
     * Verifies whether the given [userId] has at least the given [role] to access the object identified by the [urn].
     * @return True if they have it, false otherwise
     */
    fun hasPermission(
        userId: UUID,
        urn: URN,
        role: RoleType
    ) = transactionMain {
        queryUserACL(userId, role).andWhere {
            (AccessControlList.urn.column eq urn.urn)
        }
            .limit(1)
            .any()
    }

    /**
     * Verifies whether the given [userId] has at least the given [role] to access [itemId] of type [itemType].
     * @throws ValidationException if the user does not have access.
     */
    fun checkAccess(
        userId: UUID,
        itemType: Table,
        itemId: UUID,
        role: RoleType
    ) = checkAccess(userId, getURN(itemType, itemId), role)

    /**
     * Verifies whether the given [userId] has at least the given [role] to access the object identified by the [urn].
     * @throws ValidationException if the user does not have access.
     */
    fun checkAccess(
        userId: UUID,
        urn: URN,
        role: RoleType
    ) = transactionMain {
        if (!hasPermission(userId, urn, role)) {
            throw ValidationException(
                Reason.Unauthorized,
                "The specified item (identified by ${urn.urn}) does not exist or the user does not have the required ${role.name} role."
            )
        }
    }

    /**
     * Returns the ACL for the object with [urn].
     */
    fun getEntries(urn: URN): List<AccessControlEntry> = transactionMain {
        AccessControlEntry.find {
            AccessControlList.urn.column eq urn.urn
        }.toList()
    }

    /**
     * Returns the ACL for the object of [itemType] with [itemId].
     */
    fun getEntries(itemType: Table, itemId: UUID): List<AccessControlEntry> =
        getEntries(getURN(itemType, itemId))

    /**
     * Adds an ACL entry for the object with [urn] for [groupId] and sets the [role].
     */
    fun addEntry(urn: URN, groupId: UUID, role: RoleType): AccessControlEntry =
        transactionMain {
            AccessControlEntry.new {
                this.urn = urn
                this.group = Group[groupId]
                this.role = role.role
            }
        }

    /**
     * Adds an ACL entry for the object of [itemType] with [itemId] for [groupId] and [role].
     */
    fun addEntry(itemType: Table, itemId: UUID, groupId: UUID, role: RoleType): AccessControlEntry =
        addEntry(getURN(itemType, itemId), groupId, role)

    /**
     * Changes an ACL entry with [id].
     */
    fun updateEntry(id: UUID, update: (ace: AccessControlEntry) -> Unit) =
        transactionMain {
            update(AccessControlEntry[id])
        }

    /**
     * Changes an ACL entry for the object with [urn] for [groupId] and sets the [role].
     */
    fun updateEntry(urn: URN, groupId: UUID, role: RoleType): AccessControlEntry =
        transactionMain {
            val ace = AccessControlEntry.find {
                (AccessControlList.urn.column eq urn.urn) and (AccessControlList.group_id eq groupId)
            }
                .limit(1)
                .firstOrNull()
                .validateNotNull(Reason.ResourceNotFound) { "Access control entry is not found." }
            ace.role = role.role

            ace
        }

    /**
     * Changes an ACL entry for the object of [itemType] with [itemId] for [groupId] and sets the [role].
     */
    fun updateEntry(itemType: Table, itemId: UUID, groupId: UUID, role: RoleType): AccessControlEntry =
        updateEntry(getURN(itemType, itemId), groupId, role)

    /**
     * Deletes the ACL entry with [id].
     * @throws ValidationException if the ACL entry does not exist.
     */
    fun removeEntry(id: UUID) = transactionMain {
        AccessControlList.deleteWhere {
            AccessControlList.id eq id
        }.validate(1, Reason.ResourceNotFound) { "Access control entry is not found." }
    }

    /**
     * Deletes the ACL entry for the object with [urn] for [groupId].
     * @throws ValidationException if the ACL entry does not exist.
     */
    fun removeEntry(urn: URN, groupId: UUID) = transactionMain {
        AccessControlList.deleteWhere {
            (AccessControlList.urn.column eq urn.urn) and (AccessControlList.group_id eq groupId)
        }.validate(1, Reason.ResourceNotFound) { "Access control entry is not found." }
    }

    /**
     * Deletes the ACL entry for the object of [itemType] with [itemId] for [groupId].
     * @throws ValidationException if the ACL entry does not exist.
     */
    fun removeEntry(itemType: Table, itemId: UUID, groupId: UUID) =
        removeEntry(getURN(itemType, itemId), groupId)


    /**
     * Deletes all ACL entries for the object with [urn].
     */
    fun removeEntries(urn: URN) = transactionMain {
        AccessControlList.deleteWhere {
            AccessControlList.urn.column eq urn.urn
        }.validateNot(0, Reason.ResourceNotFound) { "Access control entry is not found." }
    }


    /**
     * Deletes all ACL entries for the object of [itemType] with [itemId].
     */
    fun removeEntries(itemType: Table, itemId: UUID) = removeEntries(getURN(itemType, itemId))

    fun getURN(itemType: Table, itemId: UUID) = URN("urn:processm:db/${itemType.tableName}/$itemId")

    /**
     * Given a URN and a userId, returns Groups that could plausibly be added to the ACL for the URN by the userId
     *
     * The returned list consists of all the shared groups belonging to any of the organizations of the given user and all
     * the private groups of the users of these organizations, except those groups that already have an entry in the ACL
     * for the given urn
     */
    fun getAvailableGroups(urn: URN, userId: UUID): SizedIterable<Group> =
        // all groups assigned - directly or indirectly - to any of the organizations of the user except those for which an ACE is already defined for the given URN
        transactionMain {
            // organizations the user is a member of
            val organizations = (UsersInGroups innerJoin Groups).slice(Groups.organizationId).select {
                (UsersInGroups.userId eq userId) and Groups.organizationId.isNotNull()
            }
            // these organizations + their ascendants, i.e., super-organizations
            val ascendantsOrSelf = OrganizationsDescendants
                .slice(OrganizationsDescendants.superOrganizationId)
                .select {
                    OrganizationsDescendants.subOrganizationId inSubQuery organizations
                }.union(organizations)
            // groups of these organizations
            val organizationGroups =
                Groups.slice(Groups.id).select { Groups.organizationId inSubQuery ascendantsOrSelf }
            // all users belonging to these groups
            val usersOfOrganizations1 = UsersInGroups.slice(UsersInGroups.userId)
                .select { UsersInGroups.groupId inSubQuery organizationGroups }
            // all users belonging to the organizations (inc. ascendants) according to UsersRolesInOrganizations
            val usersOfOrganizations2 = UsersRolesInOrganizations.slice(UsersRolesInOrganizations.userId)
                .select { UsersRolesInOrganizations.organizationId inSubQuery ascendantsOrSelf }
            // private groups of all possibly relevant users
            val userGroups = (UsersInGroups innerJoin Groups).slice(Groups.id)
                .select { (Groups.isImplicit eq true) and ((UsersInGroups.userId inSubQuery usersOfOrganizations1) or (UsersInGroups.userId inSubQuery usersOfOrganizations2)) }

            // groups that already have ACE for this particular URN
            val groupsAlreadyHavingACE =
                AccessControlList.slice(AccessControlList.group_id).select { AccessControlList.urn.column eq urn.urn }

            // all possibly relevant groups - both private and shared - except those that already have an entry in the ACL for the given URN
            val groups =
                Groups.select { ((Groups.id inSubQuery organizationGroups) or (Groups.id inSubQuery userGroups)) and (Groups.id notInSubQuery groupsAlreadyHavingACE) }
                    .withDistinct()

            return@transactionMain Group.wrapRows(groups)
        }
}
