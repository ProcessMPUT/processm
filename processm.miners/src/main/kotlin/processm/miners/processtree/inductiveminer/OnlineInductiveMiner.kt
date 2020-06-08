package processm.miners.processtree.inductiveminer

import processm.core.log.hierarchical.LogInputStream
import processm.core.models.processtree.ProcessTree
import processm.core.models.processtree.ProcessTreeActivity
import processm.core.models.processtree.ProcessTreeSimplifier
import processm.miners.processtree.directlyfollowsgraph.DirectlyFollowsGraph

/**
 * Online Inductive Miner
 */
class OnlineInductiveMiner : InductiveMiner() {
    /**
     * Internal structure of process tree
     * Will be used as memory to be able to modify tree in real-time.
     */
    private lateinit var model: DirectlyFollowsSubGraph

    /**
     * Directly-follows graph used by Inductive Miner
     * It should be stored as part of miner - we need modify it after each step.
     */
    private val dfg = DirectlyFollowsGraph()

    /**
     * Auxiliary variable.
     * Indicates whether the statistics of connections between the pair of activities
     * have been modified during the data analysis.
     */
    private var changedStatistics = false

    /**
     * Given log collection convert to process tree structure.
     */
    override fun processLog(logsCollection: LogInputStream) {
        discover(logsCollection)
    }

    /**
     * Result - built process tree based on given log.
     */
    override val result: ProcessTree by lazy {
        // TODO: use previous built structure
        val tree = ProcessTree(assignChildrenToNode(model))
        ProcessTreeSimplifier().simplify(tree)

        return@lazy tree
    }

    /**
     * Discover new process tree based on already stored tree and current directly-follows graph.
     *
     * `increaseTraces` parameter is responsible for the direction of changes
     * When true - adding new trace. Otherwise, remove trace from model's memory.
     */
    fun discover(log: LogInputStream, increaseTraces: Boolean = true) {
        // Statistics changed
        changedStatistics = true

        // Calculate diff and changes list
        val diff = when (increaseTraces) {
            true -> dfg.discoverDiff(log)
            false -> dfg.discoverRemovedPartOfGraph(log)
        }

        if (diff == null) {
            val activities = dfg.graph.rows.toHashSet().also {
                it.addAll(dfg.startActivities.keys)
                it.addAll(dfg.endActivities.keys)
            }

            // Rebuild tree - changes are too big
            model = DirectlyFollowsSubGraph(
                activities = activities,
                dfg = dfg
            )

            // New tree - statistics inside tree
            changedStatistics = false
        } else if (diff.isNotEmpty()) {
            // Detect affected by change activities
            val affectedActivities = detectAffectedActivities(diff)

            // Find where rebuild graph
            val subGraphToRebuild = breadthFirstSearchMinimalCommonSubGraph(affectedActivities, model)

            // Rebuild subGraph
            subGraphToRebuild.detectCuts()
        }
    }

    /**
     * Breadth first search iterative.
     * Find minimal common subGraph based on changed activities.
     */
    private fun breadthFirstSearchMinimalCommonSubGraph(
        affectedActivities: Collection<ProcessTreeActivity>,
        root: DirectlyFollowsSubGraph
    ): DirectlyFollowsSubGraph {
        // Init selected subGraph as given tree's root
        var selectedSubGraph = root

        while (true) {
            // Analyze subGraph - should contain all activities affected by changed connections
            selectedSubGraph =
                selectedSubGraph.children.firstOrNull { it.activities.containsAll(affectedActivities) } ?: break
        }

        // Return selected minimal common subGraph
        return selectedSubGraph
    }

    /**
     * Detect activities affected by the changes.
     */
    private fun detectAffectedActivities(pairs: Collection<Pair<ProcessTreeActivity, ProcessTreeActivity>>): Collection<ProcessTreeActivity> {
        val affectedActivities = mutableSetOf<ProcessTreeActivity>()
        pairs.forEach { (from, to) ->
            affectedActivities.add(from)
            affectedActivities.add(to)
        }

        return affectedActivities
    }
}