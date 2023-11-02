package processm.etl.metamodel

import org.jetbrains.exposed.dao.id.EntityID

data class Arc(val sourceClass: EntityID<Int>, val attributeName: String, val targetClass: EntityID<Int>)