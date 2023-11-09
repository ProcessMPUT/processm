package processm.etl.metamodel

import org.jetbrains.exposed.dao.id.EntityID
import processm.dbmodels.models.Classes

data class RemoteObjectID(val objectId: String, val classId: EntityID<Int>) {
    companion object {
        const val DELIMITER = '_'
    }
}

inline fun RemoteObjectID.toDB() = "${classId}${RemoteObjectID.DELIMITER}$objectId"

inline fun String.parseAsRemoteObjectID(): RemoteObjectID {
    val a = split(RemoteObjectID.DELIMITER, limit = 2)
    return RemoteObjectID(a[1], EntityID(a[0].toInt(), Classes))
}