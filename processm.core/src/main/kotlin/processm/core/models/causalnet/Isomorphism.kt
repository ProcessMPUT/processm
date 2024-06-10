package processm.core.models.causalnet

import processm.helpers.ImmutableSet
import processm.helpers.mapToSet
import java.util.*
import kotlin.collections.component1
import kotlin.collections.component2
import kotlin.collections.set


internal class Isomorphism(private val left: CausalNet, private val right: CausalNet) {

    /**
     * A map that forbids replacing values already present in it.
     */
    private class RobustMap<K, V>(initial: Map<K, V>) : HashMap<K, V>(initial) {
        override fun put(key: K, value: V): V? {
            val old = super.put(key, value)
            if (old === null || old === value)
                return old
            else
                throw IllegalStateException()
        }
    }

    /**
     * Returns features that describe [n] in the context of [cnet].
     * For two nodes [l] of [left] and [r] of [right], if [left] is isomorphic with [right] and [l] maps to [r], then their features are identical.
     * The opposite doesn't necessarily hold.
     */
    private fun features(cnet: CausalNet, n: Node): List<Int> {
        val nIncoming = cnet.incoming[n].orEmpty().size
        val nOutgoing = cnet.outgoing[n].orEmpty().size
        val nSplits = cnet.splits[n].orEmpty().size
        val nJoins = cnet.joins[n].orEmpty().size
        return listOf(nIncoming, nOutgoing, nSplits, nJoins)
    }

    private class State(val l2r: RobustMap<Node, Node>, leftByFeature: Map<List<Int>, List<Node>>, rightByFeature: Map<List<Int>, List<Node>>) {
        var leftByFeature: Map<List<Int>, List<Node>> = leftByFeature
            private set

        var rightByFeature: Map<List<Int>, List<Node>> = rightByFeature
            private set

        /**
         * Makes a deep copy of [this]
         */
        fun copy(): State = State(RobustMap(l2r),
                leftByFeature.mapValues { ArrayList(it.value) },
                rightByFeature.mapValues { ArrayList(it.value) }
        )

        /**
         * Removes nodes already present in [l2r] from [leftByFeature] and [rightByFeature], to remove them for further consideration
         */
        fun update() {
            leftByFeature = leftByFeature.mapValues { it.value - l2r.keys }
            rightByFeature = rightByFeature.mapValues { it.value - l2r.values }
        }

    }

    /**
     * Returns all incoming dependencies, outgoing dependencies, splits and joins for [l] of [left] that can be fully mapped to [right].
     * The result is mapped to [right]
     */
    private fun knownInLeft(s: State, l: Node): List<Collection<Any>> {
        val lin = left.incoming[l].orEmpty().mapNotNullTo(HashSet()) { s.l2r[it.source] }
        val lout = left.outgoing[l].orEmpty().mapNotNullTo(HashSet()) { s.l2r[it.target] }
        val lsplits = left.splits[l].orEmpty()
            .filter { split -> split.targets.all { it in s.l2r.keys } }
            .mapToSet { split -> split.targets.map { s.l2r.getValue(it) }.toSet() }
        val ljoins = left.joins[l].orEmpty()
            .filter { join -> join.sources.all { it in s.l2r.keys } }
            .mapToSet { join -> join.sources.map { s.l2r.getValue(it) }.toSet() }
        return listOf(lin, lout, lsplits, ljoins)
    }

    /**
     * Returns all incoming dependencies, outgoing dependencies, splits and joins for [r] of [right] that can be fully mapped to [left].
     * The result is mapped to [right] (i.e., left intact).
     */
    private fun knownInRight(s: State, r: Node): List<Collection<Any>> {
        val rin = right.incoming[r].orEmpty().map { it.source }.filterTo(HashSet()) { it in s.l2r.values }
        val rout = right.outgoing[r].orEmpty().map { it.target }.filterTo(HashSet()) { it in s.l2r.values }
        val rsplits = right.splits[r].orEmpty()
            .map { split -> ImmutableSet.of(*split.targets) }
            .filterTo(HashSet()) { split -> s.l2r.values.containsAll(split) }
        val rjoins = right.joins[r].orEmpty()
            .map { join -> ImmutableSet.of(*join.sources) }
            .filterTo(HashSet()) { join -> s.l2r.values.containsAll(join) }
        return listOf(rin, rout, rsplits, rjoins)
    }

    /**
     * Perform matching by comparing features from [features] and known objects from [knownInLeft]/[knownInRight] until saturation.
     */
    private fun saturate(state: State): Boolean {
        try {
            state.update()
            while (state.l2r.keys != left.instances) {
                var changed = false
                for ((f, l) in state.leftByFeature.filterValues { it.size == 1 }) {
                    val r = state.rightByFeature.getValue(f)
                    if (r.isEmpty())
                        return false
                    if (r.size == 1) {
                        state.l2r[l.single()] = r.single()
                        changed = true
                    }
                }
                for ((f, lc) in state.leftByFeature.filterValues { it.size > 1 }.toList().sortedBy { it.second.size }) {
                    val rc = state.rightByFeature.getValue(f)
                    for (l in lc) {
                        val lknown = knownInLeft(state, l)
                        val remaining = rc.filter { lknown == knownInRight(state, it) }
                        if (remaining.isEmpty())
                            return false
                        if (remaining.size == 1) {
                            state.l2r[l] = remaining.single()
                            changed = true
                        }
                    }
                }
                if (changed) {
                    state.update()
                } else {
                    break
                }
            }
            return true
        } catch (e: IllegalStateException) {
            return false
        }
    }

    /**
     * Perform the search by switching between saturating and guessing
     */
    private fun match(initialstate: State): Map<Node, Node>? {
        val queue = ArrayDeque<State>()
        queue.add(initialstate)
        while (!queue.isEmpty()) {
            val state = queue.poll()
            if (!saturate(state))
                return null
            if (state.l2r.keys == left.instances) {
                // I wonder if it is possible to come here more than once and succeed the whole match, i.e., if it is really possible to find two complete matches, but only one valid
                val rewritten = MutableCausalNet(start = right.start, end = right.end)
                rewritten.copyFrom(left) { state.l2r.getValue(it) }
                if (right.structurallyEquals(rewritten))
                    return state.l2r
            } else {
                for ((f, lc) in state.leftByFeature.filterValues { it.size > 1 }.toList().sortedBy { it.second.size }) {
                    val l = lc.first()
                    val rc = state.rightByFeature.getValue(f)
                    for (r in rc) {
                        val copy = state.copy()
                        copy.l2r[l] = r
                        queue.add(copy)
                    }
                }
            }
        }
        return null
    }

    /**
     * Returns mapping from [left] to [right] if the two causal nets are isomorphic and null otherwise.
     */
    fun run(initial: Map<Node, Node>): Map<Node, Node>? {
        if (left.instances.size != right.instances.size ||
            left.dependencies.size != right.dependencies.size ||
            left.splits.values.sumOf { it.size } != right.splits.values.sumOf { it.size } ||
            left.joins.values.sumOf { it.size } != right.joins.values.sumOf { it.size }
        )
            return null
        val leftByFeature: Map<List<Int>, List<Node>> = left.instances.groupBy { features(left, it) }
        val rightByFeature: Map<List<Int>, List<Node>> = right.instances.groupBy { features(right, it) }
        if (leftByFeature.keys != rightByFeature.keys)
            return null
        val state = State(RobustMap(initial), leftByFeature, rightByFeature)
        return match(state)
    }

}
