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

        // TODO
        detectCut(graph)

        // TODO
        return ProcessTree()
    }

    private fun detectCut(graph: DirectlyFollowsSubGraph) {
        if (graph.canFinishCalculationsOnSubGraph()) {
            println("FINISH")
            println(graph.finishCalculations())
        }

        val connectedComponents = graph.calculateExclusiveCut()
        // X
        if (connectedComponents !== null) {
            println("X")
            println(graph.splitIntoSubGraphs(connectedComponents))
        }

        // ->
        val stronglyConnectedComponents = graph.stronglyConnectedComponents()
        val seqAssigment = graph.calculateSequentialCut(stronglyConnectedComponents)
        if (seqAssigment !== null) {
            println("->")
            println(graph.splitIntoSubGraphs(seqAssigment))
        }

        // ^
        val parallelAssigment = graph.calculateParallelCut()
        if (parallelAssigment !== null) {
            println("^")
            println(graph.splitIntoSubGraphs(parallelAssigment))
        }

        // redo
        // TODO: redo

        // flower-model
        graph.finishWithDefaultRule()
    }
}