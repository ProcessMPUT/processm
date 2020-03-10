package processm.core.models.causalnet.verifier

import processm.core.helpers.SequenceWithMemory
import processm.core.helpers.withMemory
import processm.core.models.causalnet.Join
import processm.core.models.causalnet.Model
import processm.core.models.causalnet.Node
import java.util.*
import kotlin.collections.HashMap

typealias CausalNetSequence = List<ActivityBinding>


/**
 * Verifies properties of a causal net model.
 *
 * Computations are potentially expensive, but to minimize impact, everything is initialized lazily and then stored
 */
class Verifier(val model: Model, val useCache: Boolean = true) {

    /**
     * By definition, a causal net model is safe
     */
    val isSafe: Boolean = true
    /**
     * If there exists at least one valid sequence, there is a possibility to complete
     */
    val hasOptionToComplete: Boolean by lazy { validSequences.any() }
    /**
     * Each valid sequence, by definition, ensures proper completion
     */
    val hasProperCompletion: Boolean by lazy { validSequences.any() }
    /**
     * Based on Definition 3.12 in PM of soundness of C-nets
     */
    val hasDeadParts: Boolean by lazy { !checkAbsenceOfDeadParts() }
    /**
     * The only important thing is whether there are dead parts.
     * If there aren't, there are some valid sequences, so the rest is trivially satisfied.
     */
    val isSound: Boolean by lazy { isSafe && hasOptionToComplete && hasProperCompletion && !hasDeadParts }
    /**
     * The set of all valid sequences. There is a possiblity that this set is infinite.
     */
    val validSequences: SequenceWithMemory<CausalNetSequence> by lazy { computeSetOfValidSequences(false).withMemory() }
    /**
     * The set of all valid sequences without loops. This set should never be infinite.
     *
     * Recall that a state is a multi-set of pending obligations.
     * A loop in a sequence occurs if there is a state B such that there is a state A earlier in the sequence, such that
     * both states contains exactly the same pending obligations ignoring the number of occurences (e.g., this is the case for {a} and {a,a})
     * and B contains no less of each obligations than A.
     * In simple terms, B is A plus something.
     */
    val validLoopFreeSequences: SequenceWithMemory<CausalNetSequence> by lazy { computeSetOfValidSequences(true).withMemory() }

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

    private fun isBoringSuperset(subset: State, superset: State): Boolean {
        return subset.uniqueSet() == superset.uniqueSet() && superset.containsAll(subset)
    }

    private val cache = HashMap<State, List<ActivityBinding>>()

    /**
     * Compute possible extensions for a given valid sequence, according to Definition 3.11 in PM
     */
    private fun extensions(input: CausalNetSequence, avoidLoops: Boolean): List<ActivityBinding> {
        val currentState = input.last().state
        if (useCache) {
            val fromCache = cache[currentState]
            if (fromCache != null)
                return fromCache
        }
        val result = ArrayList<ActivityBinding>()
        val candidates = currentState.map { it.second }.intersect(model.joins.keys)
        for (ak in candidates) {
            for (join in model.joins.getValue(ak)) {
                val expected = join.sources.map { it to ak }
                if (currentState.containsAll(expected)) {
                    val splits = model.splits[ak]
                    if (splits != null) {
                        for (split in splits) {
                            val ab = ActivityBinding(ak, join.sources, split.targets, currentState)
                            if (!avoidLoops || input.all { !isBoringSuperset(it.state, ab.state) })
                                result.add(ab)
                        }
                    } else {
                        val ab = ActivityBinding(ak, join.sources, setOf(), currentState)
                        if (!avoidLoops || input.all { !isBoringSuperset(it.state, ab.state) })
                            result.add(ab)
                    }
                }
            }
        }
        if (useCache) {
            cache[currentState] = result
        }
        return result
    }

    /**
     * Lazily compute all valid sequences according to Definition 3.11 in PM
     *
     * There is possiblity that there are infinitely many such sequences.
     * To circumvent this, this function uses breadth-first search (BFS) and lazy evaluation
     */
    private fun computeSetOfValidSequences(avoidLoops: Boolean): Sequence<CausalNetSequence> {
        val queue = ArrayDeque<CausalNetSequence>()
        queue.addAll(model
            .splits.getOrDefault(model.start, setOf())
            .map { split ->
                listOf(ActivityBinding(model.start, setOf(), split.targets, State()))
            })
        return sequence {
            while (queue.isNotEmpty()) {
                val current = queue.pollFirst()
                extensions(current, avoidLoops).forEach { last ->
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