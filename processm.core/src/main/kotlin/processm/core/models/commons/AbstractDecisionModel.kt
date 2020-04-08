package processm.core.models.commons

import processm.core.log.hierarchical.Trace


interface AbstractDecisionModel {

    fun train(trace: Trace, decisions: Sequence<AbstractDecision>)
    fun explain(trace: Trace, decisions: Sequence<AbstractDecision>): Sequence<AbstractExplanation>

}