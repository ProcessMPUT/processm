package processm.core.verifiers.causalnet

import processm.core.helpers.SequenceWithMemory
import processm.core.helpers.mapToSet
import processm.core.helpers.withMemory
import processm.core.logging.logger
import processm.core.models.causalnet.*
import java.util.*
import kotlin.collections.HashMap

typealias CausalNetSequence = List<ActivityBinding>


/**
 * Verifies properties of a causal net model.
 *
 * Computations are potentially expensive, but to minimize impact, everything is initialized lazily and then stored
 */
class CausalNetVerifierImpl(val model: CausalNet, val useCache: Boolean = true) {

    /**
     * By definition, a causal net model is safe
     */
    val isSafe: Boolean = true

    /**
     * If there exists at least one valid sequence, there is a possibility to complete
     */
    val hasOptionToComplete: Boolean by lazy { hasSingleStart() && hasSingleEnd() && validSequences.any() }

    /**
     * Each valid sequence, by definition, ensures proper completion
     */
    val hasProperCompletion: Boolean by lazy { validSequences.any() }

    /**
     * Based on Definition 3.8 and Definition 3.12 in PM of soundness of C-nets
     */
    val noDeadParts: Boolean by lazy { allDependenciesUsed() && checkAbsenceOfDeadParts() }

    /**
     * Based on Definition 3.8 and Definition 3.12 in PM of soundness of C-nets
     */
    val hasDeadParts: Boolean by lazy { !noDeadParts }

    /**
     * The only important thing is whether there are dead parts.
     * If there aren't, there are some valid sequences, so the rest is trivially satisfied.
     */
    val isSound: Boolean by lazy { isSafe && hasOptionToComplete && hasProperCompletion && !hasDeadParts }

    /**
     * The set of all valid sequences. There is a possiblity that this set is infinite.
     */
    val validSequences: SequenceWithMemory<CausalNetSequence> by lazy {
        computeSetOfValidSequences(false, false).withMemory()
    }

    /**
     * The set of all valid sequences without loops. This set should never be infinite.
     *
     * Recall that a state is a multi-set of pending obligations.
     * A loop in a sequence occurs if there is a state B such that there is a state A earlier in the sequence, such that
     * both states contains exactly the same pending obligations ignoring the number of occurences (e.g., this is the case for {a} and {a,a})
     * and B contains no less of each obligations than A.
     * In simple terms, B is A plus something.
     */
    val validLoopFreeSequences: SequenceWithMemory<CausalNetSequence> by lazy {
        computeSetOfValidSequences(true, false).withMemory()
    }

    /**
     * @see processm.core.verifiers.CausalNetVerificationReport.validLoopFreeSequencesWithArbitrarySerialization
     */
    val validLoopFreeSequencesWithArbitrarySerialization: SequenceWithMemory<CausalNetSequence> by lazy {
        computeSetOfValidSequences(true, true).withMemory()
    }

    /**
     * True if, in the dependency graph, every node is reachable from start and end is reachable from every node.
     *
     * This is not consistent with the usual definition of connectivity for a directed graph, but seems to resemble it
     * and I'm hacking it for the lack of a better term.
     */
    val isConnected: Boolean by lazy {
        isEveryNodeReachableFromStart && isEndReachableFromEveryNode
    }

    /**
     * True if, starting from start, one can visit all nodes while traveling forward through dependencies
     */
    val isEveryNodeReachableFromStart: Boolean by lazy {
        val queue = ArrayDeque<Node>()
        val visited = HashSet<Node>()
        queue.add(model.start)
        while (!queue.isEmpty()) {
            val n = queue.poll()
            if (visited.contains(n))
                continue
            visited.add(n)
            queue.addAll(model.outgoing[n].orEmpty().map { it.target })
        }
        return@lazy visited.containsAll(model.instances)
    }

    /**
     * True if, starting from end, one can visit all nodes while traveling backwards through dependencies
     */
    val isEndReachableFromEveryNode: Boolean by lazy {
        val queue = ArrayDeque<Node>()
        val visited = HashSet<Node>()
        queue.add(model.end)
        while (!queue.isEmpty()) {
            val n = queue.poll()
            if (visited.contains(n))
                continue
            visited.add(n)
            queue.addAll(model.incoming[n].orEmpty().map { it.source })
        }
        return@lazy visited == model.instances
    }

    /**
     * True if the structure of the causal net is correct:
     *
     * * has a single start,
     * * has a single end,
     * * all dependencies are used in splits and joins,
     * * the underlying dependency graph is connected
     */
    val isStructurallySound: Boolean by lazy {
        allDependenciesUsed() && hasSingleEnd() && hasSingleStart() && isConnected
    }

    /**
     * Based on Definition 3.8 in PM
     */
    fun allDependenciesUsed(): Boolean {
        val splitDependencies = getDependencies(model.splits.values)
        val joinDependencies = getDependencies(model.joins.values)
        val allDependencies = model.dependencies
        return splitDependencies == allDependencies && joinDependencies == allDependencies
    }

    private fun getDependencies(bindingCollection: Collection<Set<Binding>>): Set<Dependency> {
        if (bindingCollection.isEmpty())
            return emptySet()

        val dependencies = HashSet<Dependency>()
        for (setOfBindings in bindingCollection)
            for (binding in setOfBindings)
                dependencies.addAll(binding.dependencies)
        return dependencies
    }

    /**
     * Returns dependencies unused in splits. A helper for [isStructurallySound].
     */
    val dependenciesUnusedInSplits
        get() = model.dependencies - model.splits.values.flatten().flatMap { it.dependencies }.toSet()

    /**
     * Returns dependencies unused in joins. A helper for [isStructurallySound].
     */
    val dependenciesUnusedInJoins
        get() = model.dependencies - model.joins.values.flatten().flatMap { it.dependencies }.toSet()

    /**
     * Over all nodes, there should be exactly one with no predecessor
     */
    private fun hasSingleStart(): Boolean {
        val starts = model.instances.filter { model.incoming[it].isNullOrEmpty() }
        if (starts.size == 1)
            return true
        logger().debug("There is an unexpected number of starts: $starts")
        return false
    }

    /**
     * Over all nodes, there should be exactly one with no successor
     */
    private fun hasSingleEnd(): Boolean {
        val ends = model.instances.filter { model.outgoing[it].isNullOrEmpty() }
        if (ends.size == 1)
            return true
        logger().debug("There is an unexpected number of ends: $ends")
        return false
    }

    private fun checkAbsenceOfDeadParts(seqs: Sequence<CausalNetSequence>): Boolean {
        return model.instances.asSequence()
            .map { a ->
                if (model.joins.containsKey(a))
                    a to model.joins.getValue(a).map { join -> join.sources }
                else
                    a to listOf(setOf())
            }.all { (a, joins) ->
                joins.all { join ->
                    seqs.any { seq -> seq.any { ab -> ab.a == a && ab.i == join } }
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
                            seqs.any { seq -> seq.any { ab -> ab.a == a && ab.o == split } }
                        }
                    }
    }

    private fun checkAbsenceOfDeadParts(): Boolean {
        if (checkAbsenceOfDeadParts(validLoopFreeSequencesWithArbitrarySerialization))
            return true
        return checkAbsenceOfDeadParts(validSequences)
    }

    private class CausalNetSequenceWithHash(other: CausalNetSequenceWithHash? = null) {
        private val _data: ArrayList<ActivityBinding> = ArrayList(other?._data ?: emptyList())
        private val states: HashMap<Int, ArrayList<CausalNetState>> = HashMap(other?.states ?: emptyMap())
        val data: List<ActivityBinding> = Collections.unmodifiableList(_data)

        fun add(ab: ActivityBinding) {
            _data.add(ab)
            states.computeIfAbsent(ab.state.uniqueSet().hashCode(), { ArrayList() }).add(ab.state)
        }

        fun containsBoringSubset(superset: CausalNetState): Boolean {
            val candidates = states[superset.uniqueSet().hashCode()]
            if (!candidates.isNullOrEmpty()) {
                return candidates.any { superset.containsAll(it) }
            }
            return false
        }
    }

    private val cache = HashMap<CausalNetState, List<ActivityBinding>>()

    /**
     * Compute possible extensions for a given valid sequence, according to Definition 3.11 in PM
     */
    private fun extensions(input: CausalNetSequenceWithHash, avoidLoops: Boolean): List<ActivityBinding> {
        val currentState = input.data.last().state
        if (useCache) {
            val fromCache = cache[currentState]
            if (fromCache != null)
                return if (avoidLoops)
                    fromCache.filter { ab -> !input.containsBoringSubset(ab.state) }
                else
                    fromCache
        }
        val result = ArrayList<ActivityBinding>()
        val candidates = currentState.map { it.target }.intersect(model.joins.keys)
        for (ak in candidates) {
            for (join in model.joins.getValue(ak)) {
                if (currentState.containsAll(join.dependencies)) {
                    val splits = model.splits[ak]
                    if (splits != null) {
                        for (split in splits) {
                            val ab = ActivityBinding(ak, join.sources, split.targets, currentState)
                            result.add(ab)
                        }
                    } else {
                        val ab = ActivityBinding(ak, join.sources, setOf(), currentState)
                        result.add(ab)
                    }
                }
            }
        }
        if (useCache) {
            cache[currentState] = result
        }
        return if (avoidLoops)
            result.filter { ab -> !input.containsBoringSubset(ab.state) }
        else
            result
    }

    /**
     * Lazily compute all valid sequences according to Definition 3.11 in PM
     *
     * There is possiblity that there are infinitely many such sequences.
     * To circumvent this, this function uses breadth-first search (BFS) and lazy evaluation
     */
    private fun computeSetOfValidSequences(
        avoidLoops: Boolean,
        chooseArbitrarySerialization: Boolean
    ): Sequence<CausalNetSequence> {
        val queue = ArrayDeque<CausalNetSequenceWithHash>()
        queue.addAll(model
            .splits.getOrDefault(model.start, setOf())
            .map { split ->
                val tmp = CausalNetSequenceWithHash()
                tmp.add(ActivityBinding(model.start, setOf(), split.targets, CausalNetStateImpl()))
                tmp
            })
        val beenThereDoneThat = HashSet<Pair<Set<Node>, Set<Dependency>>>()
        return sequence {
            while (queue.isNotEmpty()) {
                val current = queue.pollFirst()
                if (chooseArbitrarySerialization) {
                    val key = current.data.mapToSet { it.a } to current.data.last().state.uniqueSet()
                    if (beenThereDoneThat.contains(key))
                        continue
                    beenThereDoneThat.add(key)
                }
                extensions(current, avoidLoops).forEach { last ->
                    if (last.a == model.end) {
                        if (last.state.isEmpty())
                            yield(current.data + last)
                    } else {
                        val next = CausalNetSequenceWithHash(current)
                        next.add(last)
                        queue.addLast(next)
                    }
                }
            }
        }
    }

    /**
     * Checks whether the given list of [ActivityBinding]s is a valid, complete execution sequence for the considered model
     */
    fun isValid(seq: List<ActivityBinding>): Boolean {
        for (ab in seq) {
            val joins = model.joins[ab.a]
            if (joins.isNullOrEmpty()) {
                if (ab.i.isNotEmpty())
                    return false
            } else {
                if (ab.i.isEmpty())
                    return false
                if (!joins.any { join -> join.sources == ab.i })
                    return false
            }
            val splits = model.splits[ab.a]
            if (splits.isNullOrEmpty()) {
                if (ab.o.isNotEmpty())
                    return false
            } else {
                if (ab.o.isEmpty())
                    return false
                if (!splits.any { split -> split.targets == ab.o })
                    return false
            }
        }
        if (seq.first().i.isNotEmpty())
            return false
        with(seq.last()) {
            if (state.isNotEmpty() || o.isNotEmpty())
                return false
        }
        var i = seq.iterator()
        var prev = i.next()
        while (i.hasNext()) {
            val cur = i.next()
            if (prev.state != cur.stateBefore)
                return false
            prev = cur
        }
        return true
    }
}
