package processm.core.models.causalnet

import processm.core.log.Event
import processm.core.log.hierarchical.Trace
import processm.core.models.commons.Replayer
import java.util.*

/**
 * A simple CausalNet replayer. Unable to skip and not optimized.
 */
class BasicReplayer(override val model: CausalNet) : Replayer {

    private data class ExecutionState(
        val state: CausalNetState,
        val trace: List<Node>,
        val decisions: List<BindingDecision>
    )

    private fun matches(event: Event, node: Node): Boolean =
        (event.lifecycleTransition == "complete" || event.lifecycleTransition == null) && event.conceptName == node.name

    override fun replay(trace: Trace): Sequence<Sequence<BindingDecision>> =
        replay(trace.events.map { Node(it.conceptName.toString()) }.toList())

    fun replay(trace: List<Node>): Sequence<Sequence<BindingDecision>> = sequence {
        val queue = ArrayDeque<ExecutionState>()
        queue.add(ExecutionState(CausalNetStateImpl(), trace, emptyList<BindingDecision>()))
        while (!queue.isEmpty()) {
            val (state, remainingTrace, decisionsSoFar) = queue.poll()
            if (remainingTrace.isEmpty()) {
                if (state.isEmpty())
                    yield(decisionsSoFar.asSequence())
            } else {
                val currentEvent = remainingTrace[0]
                val rest = remainingTrace.subList(1, remainingTrace.size)
                for (ae in model.available(state)) {
                    if (ae.activity == currentEvent) {
                        val newState = CausalNetStateImpl(state)
                        newState.execute(ae.join, ae.split)
                        val dec = listOfNotNull(
                            model.joins[ae.activity]?.let {
                                BindingDecision(ae.join, DecisionPoint(ae.activity, it, false))
                            },
                            model.splits[ae.activity]?.let {
                                BindingDecision(ae.split, DecisionPoint(ae.activity, it, true))
                            }
                        )
                        if (rest.containsAll(newState.uniqueSet().map { it.target }))
                            queue.add(ExecutionState(newState, rest, decisionsSoFar + dec))
                    }
                }
            }
        }
    }
}
