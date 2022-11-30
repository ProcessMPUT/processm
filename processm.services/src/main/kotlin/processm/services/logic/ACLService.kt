package processm.services.logic

import org.jetbrains.exposed.sql.*
import processm.core.models.metadata.URN
import processm.core.persistence.connection.transactionMain
import processm.dbmodels.models.*
import java.util.*

class ACLService {

    /**
     * Creates a DB query that selects the ACL corresponding to [userId], [organizationId], and [role].
     */
    fun queryUserACL(userId: UUID, organizationId: UUID, role: RoleType): Query {
        val allowedRoles = RoleType.values().filter { it.ordinal <= role.ordinal }.map { it.role.id }

        return Groups.innerJoin(UsersInGroups)
            .join(AccessControlList, JoinType.INNER, AccessControlList.group_id, Groups.id)
            .select {
                (UsersInGroups.userId eq userId) and
                        ((Groups.isImplicit eq true) or (Groups.organizationId eq organizationId)) and
                        (AccessControlList.role_id inList allowedRoles)
            }
    }

    /**
     * Verifies whether the given [userId] has at least the given [role] to access [itemId] of type [itemType].
     * @throws ValidationException if the user does not have access.
     */
    fun checkAccess(
        userId: UUID,
        organizationId: UUID,
        itemType: Table,
        itemId: UUID,
        role: RoleType
    ) = transactionMain {
        val hasPermission = queryUserACL(userId, organizationId, role).andWhere {
            (AccessControlList.urn.column eq getURN(itemType, itemId).urn)
        }
            .limit(1)
            .any()

        if (!hasPermission) {
            throw ValidationException(
                Reason.ResourceNotFound,
                "The specified ${itemType.tableName} item does not exist or the user does not have the required ${role.name} role."
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

    private fun getURN(itemType: Table, itemId: UUID) = URN("urn:processm:db/${itemType.tableName}/$itemId")
}
