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
        currentNode: ActivityInstance,
        availableBindings: Iterable<Split>,
        partialLog: Iterable<Event>
    ): Map<Split, Double>

    /**
     * Returns the best split for the given situation
     */
    fun predictSplit(
        currentNode: ActivityInstance,
        availableBindings: Iterable<Split>,
        partialLog: Iterable<Event>
    ): Split

    /**
     * Returns a probability distribution over the joins given in [availableBindings]
     */
    fun predictJoinProbability(
        targetNode: ActivityInstance,
        availableBindings: Iterable<Join>,
        partialLog: Iterable<Event>
    ): Map<Join, Double>

    /**
     * Returns the best join for the given situation
     */
    fun predictJoin(
        targetNode: ActivityInstance,
        availableBindings: Iterable<Join>,
        partialLog: Iterable<Event>
    ): Join
}