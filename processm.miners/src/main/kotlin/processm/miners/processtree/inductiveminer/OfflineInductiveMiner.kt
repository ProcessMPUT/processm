package processm.miners.processtree.inductiveminer

import processm.core.log.hierarchical.Log
import processm.core.models.processtree.ProcessTree
import processm.core.models.processtree.ProcessTreeActivity
import processm.core.models.processtree.ProcessTreeSimplifier
import processm.core.models.processtree.SilentActivity
import processm.miners.processtree.directlyfollowsgraph.DirectlyFollowsGraph

/**
 * Inductive Miner version Offline, without log split inside.
 * Build a process tree based on directly-follows graph only.
 *
 * Without analyze statistics, it can generate not fully correct models.
 * Known issues:
 * * If the activity does not exist in all traces should be as ×(τ,activity).
 *   This IM will ignore [SilentActivity] and use only [ProcessTreeActivity].
 */
class OfflineInductiveMiner : InductiveMiner() {
    /**
     * Log which we should analyze
     */
    private lateinit var log: Iterable<Log>

    /**
     * Add reference to log file
     */
    override fun processLog(logsCollection: Iterable<Log>) {
        this.log = logsCollection
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

        return processTree
    }
}