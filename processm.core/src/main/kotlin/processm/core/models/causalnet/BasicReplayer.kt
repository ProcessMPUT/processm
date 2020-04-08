package processm.core.models.causalnet

import processm.core.log.Event
import processm.core.log.hierarchical.Trace
import processm.core.models.commons.AbstractReplayer
import java.util.*

class BasicReplayer(override val model: Model) : AbstractReplayer {

    private data class ExecutionState(
        val state: CausalNetState,
        val trace: List<Event>,
        val decisions: List<BindingDecision>
    )

    private fun matches(event: Event, node: Node): Boolean =
        (event.lifecycleTransition == "complete" || event.lifecycleTransition == null) && event.conceptName == node.name

    override fun replay(trace: Trace): Sequence<Sequence<BindingDecision>> = sequence {
        val queue = ArrayDeque<ExecutionState>()
        queue.add(ExecutionState(CausalNetState(), trace.events.toList(), emptyList<BindingDecision>()))
        while (!queue.isEmpty()) {
            val (state, remainingTrace, decisionsSoFar) = queue.poll()
            if (remainingTrace.isEmpty()) {
                if (state.isEmpty())
                    yield(decisionsSoFar.asSequence())
            } else {
                val currentEvent = remainingTrace[0]
                val rest = remainingTrace.subList(1, remainingTrace.size)
                for (ae in model.available(state)) {
                    if (matches(currentEvent, ae.activity)) {
                        val newState = CausalNetState(state)
                        newState.execute(ae.join, ae.split)
                        val dec = listOf(
                            BindingDecision(ae.join, DecisionPoint(ae.activity, model.joins[ae.activity].orEmpty())),
                            BindingDecision(ae.split, DecisionPoint(ae.activity, model.splits[ae.activity].orEmpty()))
                        )
                        queue.add(ExecutionState(newState, rest, decisionsSoFar + dec))
                    }
                }
            }
        }
    }
}