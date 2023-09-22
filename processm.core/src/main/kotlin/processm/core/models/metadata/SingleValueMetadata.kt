package processm.core.models.metadata

import org.jetbrains.exposed.dao.UUIDEntity
import org.jetbrains.exposed.dao.UUIDEntityClass
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.UUIDTable
import java.util.*

/**
 * Table for storing any single-valued metadata. Currently, only doubles are supported (from [SingleDoubleMetadata]), but
 * it can easily be extended to other datatypes.
 */
object SingleValueMetadataTable : UUIDTable("single_value_metadata") {
    /**
     * The Universal Resource Name (URN) of the object the metadata is related to.
     */
    val urn = text("urn")

    /**
     * The value of the metadata.
     */
    val doubleValue = double("double_value").nullable()

}

class SingleValueMetadata(id: EntityID<UUID>) : UUIDEntity(id) {
    companion object : UUIDEntityClass<SingleValueMetadata>(SingleValueMetadataTable)

    /**
     * The Universal Resource Name (URN) of the object the metadata is related to.
     */
    var urn by SingleValueMetadataTable.urn.transform({ it.urn }, { URN(it) })

    /**
     * The value of the metadata.
     */
    var doubleValue by SingleValueMetadataTable.doubleValue
        .transform({ it?.value }, { if (it !== null) SingleDoubleMetadata(it) else null })
}