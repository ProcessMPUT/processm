package processm.miners.processtree.inductiveminer

import processm.core.log.hierarchical.Log
import processm.core.models.processtree.*
import processm.miners.processtree.directlyfollowsgraph.DirectlyFollowsGraph

/**
 * Inductive Miner version Offline, without log split inside.
 * Build a process tree based on directly-follows graph only.
 */
class OfflineInductiveMiner : InductiveMiner {
    /**
     * Internal set of operations when we should analyze children stored in subGraph
     */
    private val nestedOperators = setOf(CutType.RedoLoop, CutType.Sequence, CutType.Parallel, CutType.Exclusive)

    /**
     * Log which we should analyze
     */
    private lateinit var log: Iterable<Log>

    /**
     * Add reference to log file
     */
    override fun processLog(log: Iterable<Log>) {
        this.log = log
    }

    /**
     * Result as process tree model lazy loaded by [discoverProcessTreeModel] function.
     */
    override val result: ProcessTree by lazy { discoverProcessTreeModel() }

    /**
     * Discover process tree model.
     * Build DFG and transform split subGraph to tree structure.
     */
    private fun discoverProcessTreeModel(): ProcessTree {
        // Build directly follows graph
        val dfg = DirectlyFollowsGraph()
        dfg.discover(this.log.asSequence())

        // DFG without activities - return empty process tree model
        if (dfg.startActivities.isEmpty() || dfg.endActivities.isEmpty()) return ProcessTree()

        // Prepare set with activities in graph
        val activities = dfg.graph.keys.toHashSet().also {
            it.addAll(dfg.startActivities.keys)
            it.addAll(dfg.endActivities.keys)
        }

        return ProcessTree(
            assignChildrenToNode(
                DirectlyFollowsSubGraph(
                    activities = activities,
                    outgoingConnections = dfg.graph,
                    initialConnections = dfg.graph,
                    initialStartActivities = dfg.startActivities.keys.toHashSet(),
                    initialEndActivities = dfg.endActivities.keys.toHashSet()
                )
            )
        )
    }

    /**
     * Assign children to node discovered by subGraph cut.
     * Found node can be operator (like exclusive choice) or activity.
     */
    private fun assignChildrenToNode(graph: DirectlyFollowsSubGraph): Node {
        val node = discoveredCutToNodeObject(graph)

        if (graph.detectedCut in nestedOperators) {
            val it = graph.children.filterNotNull().iterator()
            while (it.hasNext()) {
                with(assignChildrenToNode(it.next())) {
                    node.addChild(this)
                }
            }
        }

        return node
    }

    /**
     * Transform discovered cut in subGraph into node.
     * If activity or flower-model -> fetch if from graph.
     */
    private fun discoveredCutToNodeObject(graph: DirectlyFollowsSubGraph): Node {
        return when (graph.detectedCut) {
            CutType.Activity -> graph.finishCalculations()
            CutType.Exclusive -> Exclusive()
            CutType.Sequence -> Sequence()
            CutType.Parallel -> Parallel()
            CutType.RedoLoop -> RedoLoop()
            CutType.FlowerModel -> graph.finishWithDefaultRule()
        }
    }
}