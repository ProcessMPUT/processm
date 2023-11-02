package processm.etl.metamodel

import org.jetbrains.exposed.dao.id.EntityID

data class RemoteObjectID(val objectId: String, val classId: EntityID<Int>)