package processm.dbmodels.models

import org.jetbrains.exposed.dao.UUIDEntity
import org.jetbrains.exposed.dao.UUIDEntityClass
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.UUIDTable
import org.jetbrains.exposed.sql.and
import processm.core.models.metadata.URN
import processm.core.persistence.connection.transactionMain
import processm.dbmodels.models.AccessControlList.group_id
import processm.dbmodels.models.AccessControlList.role_id
import processm.dbmodels.models.User.Companion.transform
import java.util.*

/**
 * Represents the generic access control list.
 */
object AccessControlList : UUIDTable("access_control_list") {
    /**
     * The Universal Resource Name (URN) of the object under access control.
     */
    val urn = text("urn").transform({ it.urn }, { URN(it) })

    /**
     * The user group having the assigned role.
     */
    val group_id = reference("group_id", Groups)

    /**
     * The assigned role.
     */
    val role_id = reference("role_id", Roles)
}

/**
 * Represents the entry of a generic access control list.
 */
class AccessControlEntry(id: EntityID<UUID>) : UUIDEntity(id) {
    companion object : UUIDEntityClass<AccessControlEntry>(AccessControlList)

    /**
     * The Universal Resource Name (URN) of the object under access control.
     */
    var urn by AccessControlList.urn

    /**
     * The user group having the assigned role.
     */
    var group by Group referencedOn group_id

    /**
     * The assigned role.
     */
    var role by Role referencedOn role_id
}

/**
 * Returns true if [this] group has [role] for the object identified by [urn].
 */
fun Group.hasRole(urn: URN, role: Role): Boolean =
    transactionMain {
        AccessControlEntry.find {
            (AccessControlList.urn.column eq urn.urn) and (group_id eq this@hasRole.id) and (role_id eq role.id)
        }.any()
    }
