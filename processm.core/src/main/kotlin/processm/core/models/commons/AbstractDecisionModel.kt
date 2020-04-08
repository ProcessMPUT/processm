package processm.core.models.commons

import processm.core.log.hierarchical.Trace

/**
 * A decision model for providing explanations
 */
interface AbstractDecisionModel {

    /**
     * Use the provided trace and decisions to train the model (e.g., infer from the attributes of [trace] why such decision were made)
     */
    fun train(trace: Trace, decisions: Sequence<AbstractDecision>)

    /**
     * Provide explanations for the given [trace] and the [decisions] made during its execution
     */
    fun explain(trace: Trace, decisions: Sequence<AbstractDecision>): Sequence<AbstractExplanation>

}