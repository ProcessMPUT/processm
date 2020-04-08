package processm.core.models.commons

import processm.core.log.hierarchical.Trace


interface AbstractReplayer {

    val model:AbstractModel

    /**
     * Returns decisions that need to be made in [model] in order to arrive at [trace].
     *
     * There may be multiple ways to arrive at [trace], hence it returns a sequence of sequences.
     * It may also be impossible to replay given trace in the given model, then depending on the implementation,
     * it may return an empty sequence, or make some skips.
     */
    fun replay(trace: Trace): Sequence<Sequence<AbstractDecision>>
}