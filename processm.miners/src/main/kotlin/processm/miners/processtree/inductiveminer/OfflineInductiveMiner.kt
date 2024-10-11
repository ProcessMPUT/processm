package processm.miners.processtree.inductiveminer

import processm.core.log.hierarchical.LogInputStream
import processm.core.models.dfg.DirectlyFollowsGraph
import processm.core.models.metadata.*
import processm.core.models.processtree.ProcessTree
import processm.core.models.processtree.ProcessTreeSimplifier

/**
 * Inductive Miner version Offline, without log split inside.
 * Build a process tree based on directly-follows graph only.
 * This class is an implementation of the algorithm described in
 * Sander J. J. Leemans, et al., Scalable Process Discovery with Guarantees, Enterprise, Business-Process and
 * Information Systems Modeling, pp. 85-101, Springer, 2015.
 */
class OfflineInductiveMiner : InductiveMiner() {
    /**
     * Process log and build process tree based on it
     *
     * Runs in: O(|traces| * |activities|)
     */
    @Suppress("UNCHECKED_CAST")
    override fun processLog(log: LogInputStream): ProcessTree {
        // Build directly follows graph
        val dfg = DirectlyFollowsGraph()
        dfg.discover(log)

        // DFG without activities - return empty process tree model
        if (dfg.initialActivities.isEmpty() || dfg.finalActivities.isEmpty()) return ProcessTree()

        // Prepare set with activities in graph
        val activities = dfg.graph.rows.toHashSet().also {
            it.addAll(dfg.initialActivities.keys)
            it.addAll(dfg.finalActivities.keys)
        }

        // Discover processTree model
        val metadata = HashMap<MetadataSubject, SingleDoubleMetadata>()
        val metadataHandler = DefaultMutableMetadataHandler()
        val metadataProvider = DefaultMetadataProvider(BasicMetadata.DEPENDENCY_MEASURE, metadata)
        metadataHandler.addMetadataProvider(metadataProvider)
        val processTree = ProcessTree(
            assignChildrenToNode(
                DirectlyFollowsSubGraph(
                    activities = activities,
                    dfg = dfg
                ),
                metadata as MutableMap<MetadataSubject, MetadataValue>
            ),
            metadataHandler
        )


        // Simplify processTree model
        ProcessTreeSimplifier.simplify(processTree)

        return processTree
    }
}
