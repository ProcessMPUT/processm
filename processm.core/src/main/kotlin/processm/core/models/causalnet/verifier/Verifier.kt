package processm.core.models.causalnet.verifier

import processm.core.helpers.SequenceWithMemory
import processm.core.helpers.withMemory
import processm.core.models.causalnet.Join
import processm.core.models.causalnet.Model
import processm.core.models.causalnet.Node
import processm.core.models.causalnet.Split
import java.util.*

typealias CausalNetSequence = List<ActivityBinding>

class Verifier(val model: Model) {

    /**
     * By definition, a causal net model is safe
     */
    val safety: Boolean = true
    /**
     * If there exists at least one valid sequence, there is a possibility to complete
     */
    val optionToComplete: Boolean by lazy { validSequences.any() }
    /**
     * Each valid sequence, by definition, ensures proper completion
     */
    val properCompletion: Boolean by lazy { validSequences.any() }
    /**
     * Based on Definition 3.12 in PM of soundness of C-nets
     */
    val absenceOfDeadParts: Boolean by lazy { checkAbsenceOfDeadParts() }
    val soundness: Boolean by lazy { safety && optionToComplete && properCompletion && absenceOfDeadParts }
    /**
     * There is a possiblity that this set is infinite
     */
    val validSequences: SequenceWithMemory<CausalNetSequence> by lazy { computeSetOfValidSequences().withMemory() }

    private fun checkAbsenceOfDeadParts(): Boolean {
        return model.instances.asSequence()
            .map { a ->
                if (model.joins.containsKey(a))
                    a to model.joins.getValue(a).map { join -> join.sources }
                else
                    a to listOf(setOf())
            }.all { (a, joins) ->
                joins.all { join ->
                    validSequences.any { seq -> seq.any { ab -> ab.a == a && ab.i == join } }
                }
            } &&
                model.instances.asSequence()
                    .map { a ->
                        if (model.splits.containsKey(a))
                            a to model.splits.getValue(a).map { split -> split.targets }
                        else
                            a to listOf(setOf())
                    }.all { (a, splits) ->
                        splits.all { split ->
                            validSequences.any { seq -> seq.any { ab -> ab.a == a && ab.o == split } }
                        }
                    }
    }

    private fun successors(input: CausalNetSequence): Sequence<ActivityBinding> {
        val currentState = input.last().state
        return model.joins.asSequence().flatMap { (ak, joins) ->
            joins.asSequence()
                .map { join: Join -> join.sources }
                .filter { i: Collection<Node> -> currentState.containsAll(i times setOf(ak)) }
                .flatMap { i: Collection<Node> ->
                    if (model.splits.containsKey(ak))
                        model.splits.getValue(ak).asSequence().map { split ->
                            ActivityBinding(ak, i, split.targets, currentState)
                        }
                    else
                        sequenceOf(ActivityBinding(ak, i, setOf(), currentState))
                }
        }
    }

    private fun computeSetOfValidSequences(): Sequence<CausalNetSequence> {
        val queue = ArrayDeque<CausalNetSequence>()
        queue.addAll(model
            .splits.getValue(model.start)
            .map { split ->
                listOf(ActivityBinding(model.start, setOf(), split.targets, setOf()))
            })
        return sequence {
            while (queue.isNotEmpty()) {
                val current = queue.pollFirst()
                successors(current).forEach { last ->
                    if (last.a == model.end) {
                        if (last.state.isEmpty())
                            yield(current + last)
                    } else
                        queue.addLast(current + last)
                }
            }
        }
    }
}