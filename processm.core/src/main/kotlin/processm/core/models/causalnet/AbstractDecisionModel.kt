package processm.core.models.causalnet

import processm.core.models.causalnet.mock.Event

/**
 * An abstract class implementing [predictSplit] and [predictJoin] using argmax
 */
abstract class AbstractDecisionModel : DecisionModel {


    abstract override fun predictSplitProbability(
        currentNode: Node,
        availableBindings: Iterable<Split>,
        partialLog: Sequence<Event>
    ): Map<Split, Double>


    override fun predictSplit(
        currentNode: Node,
        availableBindings: Iterable<Split>,
        partialLog: Sequence<Event>
    ): Split {
        val e = predictSplitProbability(currentNode, availableBindings, partialLog).maxBy { it.value }
        if (e != null)
            return e.key
        else
            throw IllegalStateException("Invalid probability distribution returned by predictSplitProbability")
    }

    abstract override fun predictJoinProbability(
        targetNode: Node,
        availableBindings: Iterable<Join>,
        partialLog: Sequence<Event>
    ): Map<Join, Double>


    override fun predictJoin(
        targetNode: Node,
        availableBindings: Iterable<Join>,
        partialLog: Sequence<Event>
    ): Join {
        val e = predictJoinProbability(targetNode, availableBindings, partialLog).maxBy { it.value }
        if (e != null)
            return e.key
        else
            throw IllegalStateException("Invalid probability distribution returned by predictJoinProbability")
    }
}