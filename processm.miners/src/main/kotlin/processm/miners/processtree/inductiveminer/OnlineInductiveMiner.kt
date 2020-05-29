package processm.miners.processtree.inductiveminer

import processm.core.log.hierarchical.Log
import processm.core.models.processtree.ProcessTree
import processm.core.models.processtree.ProcessTreeActivity
import processm.core.models.processtree.ProcessTreeSimplifier
import processm.miners.processtree.directlyfollowsgraph.DirectlyFollowsGraph
import java.util.*

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
     * Given log collection convert to process tree structure.
     */
    override fun processLog(logsCollection: Iterable<Log>) {
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
     */
    fun discover(log: Iterable<Log>) {
        // Calculate diff and changes list
        val diff = dfg.discoverDiff(log.asSequence())

        if (diff == null) {
            val activities = dfg.graph.rows.toHashSet().also {
                it.addAll(dfg.startActivities.keys)
                it.addAll(dfg.endActivities.keys)
            }

            // Rebuild tree - changes are too big
            model = DirectlyFollowsSubGraph(
                activities = activities,
                initialConnections = dfg.graph,
                initialStartActivities = dfg.startActivities.keys.toHashSet(),
                initialEndActivities = dfg.endActivities.keys.toHashSet()
            )
        } else if (diff.isNotEmpty()) {
            // Detect affected by change activities
            val infectedActivities = detectInfectedActivities(diff)

            // Find where rebuild graph
            val subGraphToRebuild = deepFirstSearchMinimalCommonSubGraph(infectedActivities, model)

            // Rebuild subGraph
            subGraphToRebuild.rebuild()
        }
    }

    /**
     * Deep first search iterative.
     * Find minimal common subGraph based on changed activities.
     */
    private fun deepFirstSearchMinimalCommonSubGraph(
        infectedActivities: Collection<ProcessTreeActivity>,
        root: DirectlyFollowsSubGraph
    ): DirectlyFollowsSubGraph {
        // Init selected subGraph as given tree's root
        var selectedSubGraph = root

        // Initialize stack and add root as first element
        val stack = Stack<DirectlyFollowsSubGraph>()
        stack.push(root)

        while (stack.isNotEmpty()) {
            val subGraph = stack.pop()

            // Analyze subGraph - should contain all activities infected by changed connections
            if (subGraph.activities.containsAll(infectedActivities)) {
                // Update selected subGraph
                selectedSubGraph = subGraph

                // We can clear stack because it is not possible to find relations in two separated groups
                // ProcessTree without duplicated activities
                stack.clear()

                // Add all children to analyze in next iterations
                subGraph.children.forEach { stack.push(it) }
            }
        }

        // Return selected minimal common subGraph
        return selectedSubGraph
    }

    /**
     * Detect infected (activities affected by the change) activities.
     */
    private fun detectInfectedActivities(pairs: Collection<Pair<ProcessTreeActivity, ProcessTreeActivity>>): Collection<ProcessTreeActivity> {
        val infectedActivities = mutableSetOf<ProcessTreeActivity>()
        pairs.forEach { (from, to) ->
            infectedActivities.add(from)
            infectedActivities.add(to)
        }

        return infectedActivities
    }
}