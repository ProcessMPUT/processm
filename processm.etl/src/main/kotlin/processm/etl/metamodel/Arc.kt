package processm.etl.metamodel

import org.jetbrains.exposed.dao.id.EntityID

/**
 * A label of an arc in the graph of [AutomaticEtlProcessDescriptor]
 */
data class Arc(val sourceClass: EntityID<Int>, val attributeName: String, val targetClass: EntityID<Int>)