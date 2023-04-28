package processm.miners.processtree.directlyfollowsgraph

import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.transactions.transaction
import processm.core.models.processtree.ProcessTreeActivity
import processm.dbmodels.models.DFG
import processm.dbmodels.models.DFGArc
import processm.dbmodels.models.DFGEndActivity
import processm.dbmodels.models.DFGStartActivity
import java.util.*

/**
 * Stores this directly-follows graph in the given database.
 */
fun DirectlyFollowsGraph.store(database: Database): UUID = transaction(database) {
    val dfg = DFG.new { }
    for (predecessor in graph.rows) {
        for ((successor, arc) in graph.getRow(predecessor)) {
            DFGArc.new {
                this.dfg = dfg
                this.predecessor = predecessor.name
                this.successor = successor.name
                this.cardinality = arc.cardinality
            }
        }
    }

    for ((activity, arc) in initialActivities) {
        DFGStartActivity.new {
            this.dfg = dfg
            this.name = activity.name
            this.cardinality = arc.cardinality
        }
    }

    for ((activity, arc) in finalActivities) {
        DFGEndActivity.new {
            this.dfg = dfg
            this.name = activity.name
            this.cardinality = arc.cardinality
        }
    }

    dfg.id.value
}

/**
 * For internal use. Use [DirectlyFollowsGraph.load] instead.
 */
internal fun loadDFG(database: Database, id: UUID): DirectlyFollowsGraph = transaction(database) {
    val dfg = DirectlyFollowsGraph()
    val dbDfg = DFG[id]
    for (arc in dbDfg.arcs) {
        dfg.graph[ProcessTreeActivity(arc.predecessor), ProcessTreeActivity(arc.successor)] = Arc(arc.cardinality)
    }

    for (start in dbDfg.startActivities) {
        dfg.initialActivities[ProcessTreeActivity(start.name)] = Arc(start.cardinality)
    }

    for (end in dbDfg.endActivities) {
        dfg.finalActivities[ProcessTreeActivity(end.name)] = Arc(end.cardinality)
    }

    dfg
}
