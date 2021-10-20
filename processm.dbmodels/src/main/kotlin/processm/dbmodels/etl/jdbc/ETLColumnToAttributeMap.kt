package processm.dbmodels.etl.jdbc

import org.jetbrains.exposed.dao.UUIDEntity
import org.jetbrains.exposed.dao.UUIDEntityClass
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.UUIDTable
import org.jetbrains.exposed.sql.ReferenceOption
import java.util.*


object ETLColumnToAttributeMaps : UUIDTable("etl_column_to_attribute_maps") {
    val configuration =
        reference("configuration_id", ETLConfigurations, ReferenceOption.CASCADE, ReferenceOption.CASCADE)
            .index("etl_column_to_attribute_maps_configuration")
    val sourceColumn = text("source")
    val target = text("target")
    val traceId = bool("trace_id").default(false)
    val eventId = bool("event_id").default(false)
}

/**
 * A mapping of the column in a source database into the event attribute.
 */
class ETLColumnToAttributeMap(id: EntityID<UUID>) : UUIDEntity(id) {
    companion object : UUIDEntityClass<ETLColumnToAttributeMap>(ETLColumnToAttributeMaps)

    var configuration by ETLConfiguration referencedOn ETLColumnToAttributeMaps.configuration

    /**
     * The source column name.
     */
    var sourceColumn by ETLColumnToAttributeMaps.sourceColumn

    /**
     * The target attribute name.
     */
    var target by ETLColumnToAttributeMaps.target

    /**
     * A flag indicating that the [sourceColumn] stores the trace id.
     */
    var traceId by ETLColumnToAttributeMaps.traceId

    /**
     * A flag indicating that the [sourceColumn] stores the event id.
     */
    var eventId by ETLColumnToAttributeMaps.eventId
}

/**
 * Yields a map from the name of the source column into the column descriptor.
 */
fun Iterable<ETLColumnToAttributeMap>.toMap(): Map<String, ETLColumnToAttributeMap> =
    this.associateBy(ETLColumnToAttributeMap::sourceColumn)
