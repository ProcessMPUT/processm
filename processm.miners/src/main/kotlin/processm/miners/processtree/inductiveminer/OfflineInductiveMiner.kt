package processm.miners.processtree.inductiveminer

import processm.core.log.hierarchical.LogInputStream
import processm.core.models.processtree.ProcessTree
import processm.core.models.processtree.ProcessTreeSimplifier
import processm.miners.processtree.directlyfollowsgraph.DirectlyFollowsGraph

/**
 * Inductive Miner version Offline, without log split inside.
 * Build a process tree based on directly-follows graph only.
 */
class OfflineInductiveMiner : InductiveMiner() {
    /**
     * Process log and build process tree based on it
     *
     * Runs in: O(|traces| * |activities|)
     */
    override fun processLog(logsCollection: LogInputStream, increaseTraces: Boolean): ProcessTree {
        // Build directly follows graph
        val dfg = DirectlyFollowsGraph()
        dfg.discover(logsCollection)

        // DFG without activities - return empty process tree model
        if (dfg.startActivities.isEmpty() || dfg.endActivities.isEmpty()) return ProcessTree()

        // Prepare set with activities in graph
        val activities = dfg.graph.rows.toHashSet().also {
            it.addAll(dfg.startActivities.keys)
            it.addAll(dfg.endActivities.keys)
        }

        // Discover processTree model
        val processTree = ProcessTree(
            assignChildrenToNode(
                DirectlyFollowsSubGraph(
                    activities = activities,
                    dfg = dfg
                )
            )
        )

        // Simplify processTree model
        ProcessTreeSimplifier().simplify(processTree)

        assignActivities(processTree.root)

        return processTree
    }
}