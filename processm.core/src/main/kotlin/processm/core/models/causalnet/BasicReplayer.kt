package processm.core.models.causalnet

import processm.core.log.Event
import processm.core.log.hierarchical.Trace
import processm.core.models.commons.AbstractReplayer
import processm.core.models.metadata.DefaultMutableMetadataHandler
import java.util.*

class BasicReplayer(override val model: Model) : AbstractReplayer {

    private data class ExecutionState(val state: CausalNetState, val trace: List<Event>, val decisions: List<BindingDecision>)

    private fun matches(event: Event, node: Node): Boolean {
        //TODO reconsider
        return (event.lifecycleTransition == "complete" || event.lifecycleTransition == null) && event.conceptName == node.name
    }

    //TODO possibly move things to state and cease using Instance here
    override fun replay(trace: Trace): Sequence<Sequence<BindingDecision>> = sequence {
        val queue = ArrayDeque<ExecutionState>()
        queue.add(ExecutionState(CausalNetState(), trace.events.toList(), emptyList<BindingDecision>()))
        while (!queue.isEmpty()) {
            val (state, remainingTrace, decisionsSoFar) = queue.poll()
            if (remainingTrace.isEmpty()) {
                if(state.isEmpty())
                    yield(decisionsSoFar.asSequence())
            }
            else {
                val instance = MutableModelInstance(model, DefaultMutableMetadataHandler(), state)
                val currentEvent = remainingTrace[0]
                val rest = remainingTrace.subList(1, remainingTrace.size)
                for (ae in instance.availableActivityExecutions) {
                    if (matches(currentEvent, ae.activity)) {
//                        println("AE=$ae")
                        val tmp = MutableModelInstance(model, DefaultMutableMetadataHandler(), state)
                        tmp.execute(ae.join, ae.split)
                        val dec = listOf(
                            BindingDecision(ae.join, DecisionPoint(ae.activity, model.joins[ae.activity].orEmpty())),
                            BindingDecision(ae.split, DecisionPoint(ae.activity, model.splits[ae.activity].orEmpty()))
                        )
                        queue.add(ExecutionState(tmp.state, rest, decisionsSoFar + dec))
                    }
                }
//                println()
            }
        }
    }
}