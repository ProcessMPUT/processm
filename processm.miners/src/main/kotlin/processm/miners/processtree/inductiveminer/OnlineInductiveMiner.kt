package processm.miners.processtree.inductiveminer

import processm.core.log.hierarchical.LogInputStream
import processm.core.models.processtree.ProcessTree
import processm.core.models.processtree.ProcessTreeActivity
import processm.core.models.processtree.ProcessTreeSimplifier
import processm.miners.processtree.directlyfollowsgraph.DirectlyFollowsGraph
import processm.miners.processtree.inductiveminer.CutType.*
import java.util.*

/**
 * Online Inductive Miner
 */
class OnlineInductiveMiner : InductiveMiner() {
    companion object {
        val operatorCuts = setOf(Parallel, Sequence, Exclusive, RedoLoop)
        val activityCuts = setOf(Activity, OptionalActivity, RedoActivityAtLeastOnce, RedoActivityAtLeastZeroTimes)
    }

    /**
     * Internal structure of process tree
     * Will be used as memory to be able to modify tree in real-time.
     */
    private lateinit var model: DirectlyFollowsSubGraph

    /**
     * Internal structure of process tree
     * Will be used as memory to be able to modify tree in real-time.
     */
    private lateinit var processTree: ProcessTree

    /**
     * Directly-follows graph used by Inductive Miner
     * It should be stored as part of miner - we need modify it after each step.
     *
     * Memory usage: O(|activities|^2)
     */
    private val dfg = DirectlyFollowsGraph()

    var builtFromZero = 0
        private set
    var rebuild = 0
        private set
    var tracesNoRebuildNeeds = 0
        private set

    /**
     * Auxiliary variable.
     * Indicates whether the statistics of connections between the pair of activities
     * have been modified during the data analysis.
     */
    private var changedStatistics = false

    /**
     * Given log collection convert to process tree structure.
     *
     * Runs in: O(|traces| * |activities|^2)
     */
    override fun processLog(logsCollection: LogInputStream): ProcessTree {
        discover(logsCollection)

        // Check - apply statistics?
        if (changedStatistics) propagateStatistics()

        processTree = ProcessTree(assignChildrenToNode(model))
        ProcessTreeSimplifier().simplify(processTree)

        assignActivities(processTree.root)

        return processTree
    }

    /**
     * Discover new process tree based on already stored tree and current directly-follows graph.
     *
     * `increaseTraces` parameter is responsible for the direction of changes
     * When true - adding new trace. Otherwise, remove trace from model's memory.
     *
     * Runs in: O(|traces| * |activities|^2)
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
            builtFromZero++
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
            rebuild++
            subGraphToRebuild.detectCuts()
        } else {
            tracesNoRebuildNeeds++
        }
    }

    /**
     * Breadth first search iterative.
     * Find minimal common subGraph based on changed activities.
     *
     * Runs in: O(|affectedActivities|)
     */
    private fun breadthFirstSearchMinimalCommonSubGraph(
        affectedActivities: Collection<ProcessTreeActivity>,
        root: DirectlyFollowsSubGraph
    ): DirectlyFollowsSubGraph {
        // Init selected subGraph as given tree's root
        var selectedSubGraph = root

        while (true) {
            // Analyze subGraph - should contain all activities affected by changed connections
            if (selectedSubGraph.detectedCut !in operatorCuts) break
            selectedSubGraph =
                selectedSubGraph.children.firstOrNull { it.activities.containsAll(affectedActivities) } ?: break
        }

        // Return selected minimal common subGraph
        return selectedSubGraph
    }

    /**
     * Detect activities affected by the changes.
     *
     * Runs in: O(|activities|^2)
     */
    private fun detectAffectedActivities(pairs: Collection<Pair<ProcessTreeActivity, ProcessTreeActivity>>): Collection<ProcessTreeActivity> {
        val affectedActivities = mutableSetOf<ProcessTreeActivity>()
        pairs.forEach { (from, to) ->
            affectedActivities.add(from)
            affectedActivities.add(to)
        }

        return affectedActivities
    }

    /**
     * Propagation of statistics inside the model.
     *
     * Make changes of node value support.
     * For activity, decide to use optionality / loops.
     *
     * For exclusive choice we should also manage silent activities inside cut.
     * Add τ if missing, remove if redundant.
     *
     * BFS was used to prevent recursion.
     *
     * Runs in: O(|activities|^2)
     */
    private fun propagateStatistics() {
        val exclusiveChoicesInsideGraph = LinkedList<DirectlyFollowsSubGraph>()
        val stack = ArrayDeque<DirectlyFollowsSubGraph>()
        stack.add(model)

        while (stack.isNotEmpty()) {
            val subGraph = stack.pop()

            // Update statistics
            // Runs in O(|activities|)
            subGraph.updateCurrentTraceSupport()

            if (subGraph.detectedCut in operatorCuts) {
                if (subGraph.detectedCut == Exclusive) exclusiveChoicesInsideGraph.add(subGraph)

                // Add children if node as one of cut
                stack.addAll(subGraph.children)
            } else if (subGraph.detectedCut in activityCuts) {
                // Re-try analyze activity and decide which case we have
                subGraph.detectActivityCutType()
            }
        }

        // Analyze silent activity inside exclusive choice
        exclusiveChoicesInsideGraph.forEach { it.modifySilentActivityInsideExclusiveChoice() }
    }
}