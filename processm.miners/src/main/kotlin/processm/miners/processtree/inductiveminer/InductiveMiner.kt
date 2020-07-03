package processm.miners.processtree.inductiveminer

import processm.core.log.hierarchical.LogInputStream
import processm.core.models.processtree.*
import java.util.*

/**
 * Inductive miners common abstract implementation.
 * Can be used by:
 * - [OfflineInductiveMiner]
 * - [OnlineInductiveMiner]
 * as code-base reduces. You should use [InductiveMiner] instead of duplicating code.
 */
abstract class InductiveMiner {
    /**
     * Perform mining on a given log.
     */
    abstract fun processLog(logsCollection: LogInputStream): ProcessTree

    companion object {
        /**
         * Internal set of operations when we should analyze children stored in subGraph
         */
        private val nestedOperators =
            EnumSet.of(CutType.RedoLoop, CutType.Sequence, CutType.Parallel, CutType.Exclusive)
    }

    /**
     * Assign children to node discovered by subGraph cut.
     * Found node can be operator (like exclusive choice) or activity.
     */
    internal fun assignChildrenToNode(graph: DirectlyFollowsSubGraph): Node {
        val node = discoveredCutToNodeObject(graph)

        if (graph.detectedCut in nestedOperators) {
            val it = graph.children.iterator()
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
            CutType.OptionalActivity -> graph.finishWithOptionalActivity()
            CutType.RedoActivityAtLeastOnce -> graph.finishWithRedoActivityAlways()
            CutType.RedoActivityAtLeastZeroTimes -> graph.finishWithDefaultRule()
            CutType.FlowerModel -> graph.finishWithDefaultRule()
        }
    }
}