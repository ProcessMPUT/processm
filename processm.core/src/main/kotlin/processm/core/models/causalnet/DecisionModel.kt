package processm.core.models.causalnet

import processm.core.models.causalnet.mock.Event

/**
 * An interface for a decision model to decide on the next move for a given (partial) log
 */
interface DecisionModel {

    /**
     * Returns a probability distribution over the splits given in [availableBindings]
     */
    fun predictSplitProbability(
        currentNode: Node,
        availableBindings: Iterable<Split>,
        partialLog: Sequence<Event>
    ): Map<Split, Double>

    /**
     * Returns the best split for the given situation
     */
    fun predictSplit(
        currentNode: Node,
        availableBindings: Iterable<Split>,
        partialLog: Sequence<Event>
    ): Split

    /**
     * Returns a probability distribution over the joins given in [availableBindings]
     */
    fun predictJoinProbability(
        targetNode: Node,
        availableBindings: Iterable<Join>,
        partialLog: Sequence<Event>
    ): Map<Join, Double>

    /**
     * Returns the best join for the given situation
     */
    fun predictJoin(
        targetNode: Node,
        availableBindings: Iterable<Join>,
        partialLog: Sequence<Event>
    ): Join
}