package processm.core.models.commons

import processm.core.log.hierarchical.Trace

/**
 * A decision model for providing explanations
 */
interface DecisionModel {

    /**
     * Use the provided trace and decisions to train the model (e.g., infer from the attributes of [trace] why such decision were made)
     */
    fun train(trace: Trace, decisions: Sequence<Decision>)

    /**
     * Provide explanations for the given [trace] and the [decisions] made during its execution
     */
    fun explain(trace: Trace, decisions: Sequence<Decision>): Sequence<Explanation>

}