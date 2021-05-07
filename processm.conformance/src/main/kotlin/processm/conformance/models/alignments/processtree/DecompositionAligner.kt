package processm.conformance.models.alignments.processtree

import processm.conformance.models.alignments.*
import processm.core.helpers.*
import processm.core.log.Event
import processm.core.log.hierarchical.Trace
import processm.core.models.processtree.*

/**
 * An aligner for [ProcessTree]s that calculates alignment using subtree-based decomposition of the given tree.
 * @property model The Process tree to align with.
 * @property penalty The penalty function.
 * @property alignerFactory The factory for base aligners. The base aligner is used to produce partial aligners for
 * the parts of the decomposed [model].
 */
class DecompositionAligner(
    val model: ProcessTree,
    val penalty: PenaltyFunction = PenaltyFunction(),
    val alignerFactory: AlignerFactory = AlignerFactory { m, p, _ -> AStar(m, p) }
) : Aligner {

    companion object {
        private const val INFINITE_UPPER_BOUND = 1000000
    }

    override fun align(trace: Trace): Alignment {
        val events = trace.events.toList()
        val eventsWithExistingActivities =
            events.filter { e -> model.activities.any { a -> !a.isSilent && a.name == e.conceptName } }

        val alignment = decompose(model.root!!, eventsWithExistingActivities)
        if (events.size == eventsWithExistingActivities.size)
            return alignment

        return alignment.fillMissingEvents(events, penalty)
    }

    private fun decompose(node: Node, events: List<Event>): Alignment =
        when (node) {
            is Sequence -> decomposeSequence(node, events)
            is Exclusive -> decomposeExclusive(node, events)
            is Parallel -> decomposeParallel(node, events)
            is RedoLoop -> decomposeRedoLoop(node, events)
            else -> align(node, events)
        }

    private fun decomposeSequence(node: Sequence, events: List<Event>): Alignment {
        val decomposition = LogDecomposition(node, events)

        val valid = decomposition.nodes.indices.all { i ->
            (i + 1 until decomposition.nodes.size).all { j ->
                decomposition.eventuallyFollows(i, j) && !decomposition.eventuallyFollows(j, i)
            }
        }

        if (valid) {
            // great! it is valid to calculate subalignment for this trace
            val steps = ArrayList<Step>()
            var cost = 0


            val visited = HashSet<Int>()
            for (i in events.indices) {
                val subTraceIndex = decomposition.eventMap[i]
                assert(subTraceIndex >= 0)

                if (subTraceIndex in visited)
                    continue
                visited.add(subTraceIndex)

                val alignment = decompose(decomposition.nodes[subTraceIndex], decomposition.traces[subTraceIndex])
                steps.addAll(alignment.steps)
                cost += alignment.cost
            }

            steps.verify(events)
            return Alignment(steps, cost)
        }

        return align(node, events)
    }

    private fun decomposeExclusive(node: Exclusive, events: List<Event>): Alignment {
        val decomposition = LogDecomposition(node, events)

        // find the child for which to calculate subalignment
        val index = decomposition.eventMap.first()
        assert(index >= 0)

        // validate
        val valid = decomposition.eventMap.all { it == index || it == -1 }
        if (valid) {
            // great! it is valid to calculate subalignment for this trace
            assert(decomposition.traces[index] == events)

            val alignment = decompose(decomposition.nodes[index], decomposition.traces[index])
            alignment.steps.verify(events)
            return alignment
        }

        // try to reduce the number of options
        val possibleChildren = ArrayList<Node>(decomposition.nodes.size)
        for (i in decomposition.nodes.indices) {
            if (decomposition.traces[i].isNotEmpty() || hasSilentActivity(decomposition.nodes[i]))
                possibleChildren.add(decomposition.nodes[i])
        }
        if (possibleChildren.size < decomposition.nodes.size) {
            return align(Exclusive(*possibleChildren.toTypedArray()), events)
        }

        return align(node, events)
    }

    private fun decomposeParallel(node: Parallel, events: List<Event>): Alignment {
        val decomposition = LogDecomposition(node, events)

        val subAlignments =
            decomposition.nodes.indices.map { decompose(decomposition.nodes[it], decomposition.traces[it]) }
        val alignment = subAlignments.merge(events)
        alignment.steps.verify(events)
        return alignment
    }

    private fun decomposeRedoLoop(node: RedoLoop, events: List<Event>): Alignment {
        val decomposition = LogDecomposition(node, events)

        // fast path: one iteration of the loop
        val unique = HashSet<Int>()
        if (events.indices.all { e -> unique.add(decomposition.eventMap[e]) } && unique.size <= 1 && unique.firstOrNull() ?: 0 == 0)
            return decompose(decomposition.nodes[0], decomposition.traces[0])

        // the first and the last activity must belong to the first subtrace
        val firstSubtrace = decomposition.traces[0]
        var valid = events.first() in firstSubtrace && events.last() in firstSubtrace

        // all but the first subtraces have direct circular references to the first subtrace
        valid = valid && (1 until decomposition.nodes.size).all { i ->
            decomposition.directlyFollows[0]?.contains(i) == true && decomposition.directlyFollows[i]?.contains(0) == true
        }

        if (valid) {
            val usedChildren = ArrayList<Node>(decomposition.nodes.size)
            for (i in decomposition.nodes.indices) {
                if (decomposition.traces[i].isNotEmpty() || hasSilentActivity(decomposition.nodes[i]))
                    usedChildren.add(decomposition.nodes[i])
            }

            // use simplified loop
            if (usedChildren.size < decomposition.nodes.size) {
                return align(RedoLoop(*usedChildren.toTypedArray()), events)
            }
        }

        return align(node, events)
    }

    private fun simplify(node: Node, events: List<Event>): Node =
        when (node) {
            is Exclusive -> simplifyExclusive(node, events)
            else -> simplifyOther(node, events)
        }

    private fun simplifyExclusive(node: Exclusive, events: List<Event>): Node {
        val used = HashMap<Int, Node>(node.children.size)
        val decomposition = LogDecomposition(node, events)

        // try to reduce the number of options
        var badAlternative: Int = -1
        var badAlternativeUB: Int = Int.MAX_VALUE
        for (i in decomposition.nodes.indices) {
            if (decomposition.traces[i].isNotEmpty() || hasSilentActivity(decomposition.nodes[i])) {
                used[i] = decomposition.nodes[i]
            } else {
                val ub = upperBound(decomposition.nodes[i])
                if (ub < badAlternativeUB) {
                    badAlternative = i
                    badAlternativeUB = ub
                }
            }
        }

        // add non-matching option for the case it when skipping this alternative is less costly than replaying the trace
        // on one of the "good" alternatives
        if (badAlternative != -1)
            used[badAlternative] = decomposition.nodes[badAlternative]

        val size = used.size
        assert(size > 0)

        return when {
            size == 1 -> simplify(used.values.first(), events)
            size < node.children.size -> Exclusive(*used.values.mapToArray { simplify(it, events) })
            else -> simplifyOther(node, events)
        }
    }

    private fun simplifyOther(node: Node, events: List<Event>): Node {
        val copy = when (node) {
            is Sequence -> Sequence()
            is Exclusive -> Exclusive()
            is Parallel -> Parallel()
            is RedoLoop -> RedoLoop()
            is ProcessTreeActivity -> node
            else -> throw IllegalArgumentException("Unknown node type: ${node::class.simpleName}.")
        }

        for (child in node.children) {
            copy.addChild(simplify(child, events))
        }

        return copy
    }

    private fun upperBound(node: Node): Int =
        when (node) {
            is ProcessTreeActivity -> if (node.isSilent) penalty.silentMove else penalty.modelMove
            is Sequence, is Parallel -> node.children.sumOf(::upperBound)
            is Exclusive -> node.children.minOf(::upperBound)
            is RedoLoop -> {
                val firstUB = upperBound(node.children[0])
                if (firstUB == 0) {
                    firstUB
                } else {
                    val otherUB = node.children.listIterator(1).minOf(::upperBound)
                    if (otherUB == 0)
                        firstUB
                    else
                        INFINITE_UPPER_BOUND
                }
            }
            else -> throw IllegalArgumentException("Unknown node ${node::class.simpleName}.")
        }

    private fun align(node: Node, events: List<Event>): Alignment {
        val simplified = simplify(node, events)
        val aligner = alignerFactory(ProcessTree(simplified), penalty, SameThreadExecutorService)
        return aligner.align(Trace(events.asSequence()))
    }

    private fun hasSilentActivity(node: Node): Boolean {
        if (node is ProcessTreeActivity && node.isSilent)
            return true
        return node.children.any(::hasSilentActivity)
    }


    private class LogDecomposition(node: Node, trace: List<Event>) {
        val nodes: List<Node>
        val traces: List<List<Event>>

        /**
         * Map of indices of subtraces being in directly follows relation.
         */
        val directlyFollows: Map<Int, Set<Int>>

        /**
         * The item at position i consists of the index of the subtrace the ith event from the trace corresponds to.
         * -1 for events that do not correspond to any subtrace.
         */
        val eventMap: List<Int>

        init {
            val activities = node.children.map { collectActivitiesTo(HashSet(), it) }
            val subTraces = ArrayList<ArrayList<Event>>(activities.size)
            for (i in activities.indices)
                subTraces.add(ArrayList())
            val directlyFollows = HashMap<Int, HashSet<Int>>()
            val eventMap = ArrayList<Int>(trace.size)

            var lastSubTraceIndex = Int.MIN_VALUE
            for (event in trace) {
                val subTraceIndex = activities.indexOfFirst { event.conceptName in it }
                if (subTraceIndex >= 0) {
                    subTraces[subTraceIndex].add(event)
                    eventMap.add(subTraceIndex)

                    if (lastSubTraceIndex != Int.MIN_VALUE)
                        directlyFollows.computeIfAbsent(lastSubTraceIndex, ::HashSet).add(subTraceIndex)
                    lastSubTraceIndex = subTraceIndex
                } else {
                    eventMap.add(-1)
                }
            }

            assert(subTraces.size == node.children.size)
            assert(subTraces.sumOf(List<*>::size) == trace.size - eventMap.count { it == -1 })
            assert(eventMap.size == trace.size)

            this.nodes = node.children
            this.traces = subTraces
            this.directlyFollows = directlyFollows
            this.eventMap = eventMap
        }

        fun eventuallyFollows(subTrace: Int, follower: Int): Boolean =
            eventuallyFollows(subTrace, follower, HashSet())

        private fun eventuallyFollows(subTrace: Int, follower: Int, visited: HashSet<Int>): Boolean {
            if (!visited.add(subTrace))
                return false

            val response = directlyFollows[subTrace]
                ?.let { followers ->
                    follower in followers || followers.any { eventuallyFollows(it, follower, visited) }
                } == true

            visited.remove(subTrace)
            return response
        }

        private fun collectActivitiesTo(set: MutableSet<String>, node: Node): Set<String> {
            if (node is ProcessTreeActivity)
                set.add(node.name)
            else
                for (child in node.children)
                    collectActivitiesTo(set, child)
            return set
        }
    }
}
