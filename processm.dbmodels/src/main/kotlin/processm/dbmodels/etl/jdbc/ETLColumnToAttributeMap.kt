package processm.dbmodels.etl.jdbc

import org.jetbrains.exposed.dao.UUIDEntity
import org.jetbrains.exposed.dao.UUIDEntityClass
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.UUIDTable
import java.util.*


object ETLColumnToAttributeMaps : UUIDTable("etl_column_to_attribute_maps") {
    val configuration =
        reference("configuration_id", ETLConfigurations).index("etl_column_to_attribute_maps_configuration")
    val sourceColumn = text("source")
    val target = text("target")
    val traceId = bool("trace_id").default(false)
    val eventId = bool("event_id").default(false)
}

class ETLColumnToAttributeMap(id: EntityID<UUID>) : UUIDEntity(id) {
    companion object : UUIDEntityClass<ETLColumnToAttributeMap>(ETLColumnToAttributeMaps)

    var configuration by ETLConfiguration referencedOn ETLColumnToAttributeMaps.configuration
    var sourceColumn by ETLColumnToAttributeMaps.sourceColumn
    var target by ETLColumnToAttributeMaps.target
    var traceId by ETLColumnToAttributeMaps.traceId
    var eventId by ETLColumnToAttributeMaps.eventId
}

/**
 * Yields a map from the name of the source column into the column descriptor.
 */
fun Iterable<ETLColumnToAttributeMap>.toMap(): Map<String, ETLColumnToAttributeMap> =
    this.associateBy(ETLColumnToAttributeMap::sourceColumn)
