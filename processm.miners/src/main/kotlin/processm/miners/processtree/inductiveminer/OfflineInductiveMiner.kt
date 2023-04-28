package processm.miners.processtree.inductiveminer

import processm.core.log.hierarchical.LogInputStream
import processm.core.models.dfg.DirectlyFollowsGraph
import processm.core.models.processtree.ProcessTree
import processm.core.models.processtree.ProcessTreeSimplifier

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
    override fun processLog(logsCollection: LogInputStream): ProcessTree {
        // Build directly follows graph
        val dfg = DirectlyFollowsGraph()
        dfg.discover(logsCollection)

        // DFG without activities - return empty process tree model
        if (dfg.initialActivities.isEmpty() || dfg.finalActivities.isEmpty()) return ProcessTree()

        // Prepare set with activities in graph
        val activities = dfg.graph.rows.toHashSet().also {
            it.addAll(dfg.initialActivities.keys)
            it.addAll(dfg.finalActivities.keys)
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

        return processTree
    }
}
