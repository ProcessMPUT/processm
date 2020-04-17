package processm.miners.processtree.inductiveminer

import processm.core.log.hierarchical.Log
import processm.core.models.processtree.ProcessTree
import processm.miners.processtree.directlyfollowsgraph.DirectlyFollowsGraph

class OfflineInductiveMiner : InductiveMiner {
    private lateinit var log: Log

    override fun processLog(log: Log) {
        this.log = log
    }

    override val result: ProcessTree by lazy { discoverProcessTreeModel() }

    private fun discoverProcessTreeModel(): ProcessTree {
        // Build directly follows graph
        val dfg = DirectlyFollowsGraph()
        dfg.discover(sequenceOf(this.log))

        // DFG without activities - return empty process tree model
        if (dfg.startActivities.isEmpty() || dfg.endActivities.isEmpty()) return ProcessTree()

        // Prepare set with activities in graph
        val activities = dfg.graph.keys.toHashSet().also {
            it.addAll(dfg.startActivities.keys)
            it.addAll(dfg.endActivities.keys)
        }

        // Prepare base graph
        val graph = DirectlyFollowsSubGraph(
            activities = activities,
            outgoingConnections = dfg.graph,
            initialConnections = dfg.graph,
            initialStartActivities = dfg.startActivities.keys.toHashSet(),
            initialEndActivities = dfg.endActivities.keys.toHashSet()
        )

        // Run graph's detections
        graph.detectCuts()

        // TODO transform children from graph into nodes
        return ProcessTree()
    }
}