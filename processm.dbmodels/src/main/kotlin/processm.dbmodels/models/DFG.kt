package processm.dbmodels.models

import org.jetbrains.exposed.dao.EntityClass
import org.jetbrains.exposed.dao.LongEntity
import org.jetbrains.exposed.dao.UUIDEntity
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.LongIdTable
import org.jetbrains.exposed.dao.id.UUIDTable
import java.util.*

object DFGs : UUIDTable("directly_follows_graph")
class DFG(id: EntityID<UUID>) : UUIDEntity(id) {
    companion object : EntityClass<UUID, DFG>(DFGs)

    val arcs by DFGArc referrersOn DFGArcs.dfgId
    val startActivities by DFGStartActivity referrersOn DFGStartActivities.dfgId
    val endActivities by DFGEndActivity referrersOn DFGEndActivities.dfgId
}

object DFGArcs : LongIdTable("directly_follows_graph_arc") {
    val dfgId = reference("dfg_id", DFGs)
    val predecessor = text("predecessor")
    val successor = text("successor")
    val cardinality = integer("cardinality")
}

class DFGArc(id: EntityID<Long>) : LongEntity(id) {
    companion object : EntityClass<Long, DFGArc>(DFGArcs)

    var dfg by DFG referencedOn DFGArcs.dfgId
    var predecessor by DFGArcs.predecessor
    var successor by DFGArcs.successor
    var cardinality by DFGArcs.cardinality
}

object DFGStartActivities : LongIdTable("directly_follows_graph_start_activities") {
    val dfgId = reference("dfg_id", DFGs)
    val name = text("name")
    val cardinality = integer("cardinality")
}

class DFGStartActivity(id: EntityID<Long>) : LongEntity(id) {
    companion object : EntityClass<Long, DFGStartActivity>(DFGStartActivities)

    var dfg by DFG referencedOn DFGStartActivities.dfgId
    var name by DFGStartActivities.name
    var cardinality by DFGStartActivities.cardinality
}

object DFGEndActivities : LongIdTable("directly_follows_graph_end_activities") {
    val dfgId = reference("dfg_id", DFGs)
    val name = text("name")
    val cardinality = integer("cardinality")
}

class DFGEndActivity(id: EntityID<Long>) : LongEntity(id) {
    companion object : EntityClass<Long, DFGEndActivity>(DFGEndActivities)

    var dfg by DFG referencedOn DFGEndActivities.dfgId
    var name by DFGEndActivities.name
    var cardinality by DFGEndActivities.cardinality
}
