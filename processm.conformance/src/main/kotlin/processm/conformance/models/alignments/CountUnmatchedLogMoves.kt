package processm.conformance.models.alignments

import com.carrotsearch.hppc.IntArrayDeque
import com.carrotsearch.hppc.IntHashSet
import com.carrotsearch.hppc.ObjectIntHashMap
import com.carrotsearch.hppc.ObjectIntMap
import processm.core.log.Event
import processm.core.models.causalnet.CausalNet
import processm.core.models.causalnet.CausalNetState
import processm.core.models.commons.*
import processm.core.models.petrinet.Marking
import processm.core.models.petrinet.PetriNet
import processm.core.models.petrinet.Transition
import processm.helpers.cartesianProduct
import processm.helpers.mapToSet
import java.lang.Integer.min
import java.util.*

/**
 * A base interface for counting necessary skip moves in the model.
 */
interface CountUnmatchedLogMoves {
    /**
     * Called before each new trace
     */
    fun reset() {}

    /**
     * @param startIndex The starting index in the [trace].
     * @param trace The list of events.
     * @param prevProcessState The state of the process model.
     * @return The total number of future events for which the corresponding model moves do not exist.
     */
    fun compute(startIndex: Int, trace: List<Event>, prevProcessState: ProcessModelState, curActivity: Activity?): Int
}

abstract class DFGWrapper(val model: ProcessModel) : ProcessModel by model {
    abstract val directlyFollowsRelation: Collection<CausalArc>
    abstract val outgoing: Map<Activity, Collection<Activity>>
}

abstract class AbstractCountUnmatchedLogMoves(
    protected val model: DFGWrapper
) : CountUnmatchedLogMoves {
    /**
     * The list of sets of activities corresponding to strongly connected components in the directly follows graph.
     * https://en.wikipedia.org/wiki/Strongly_connected_component
     */
    protected val stronglyConnectedComponents: List<Set<Activity>> = stronglyConnectedComponents()

    /**
     * The mapping from activity to SCC index in [stronglyConnectedComponents] list.
     */
    protected val activityToSCC: ObjectIntMap<Activity> = activityToSCC()

    protected val conceptNameToSCC = conceptNameToSCC()

    /**
     * The directly-follows relation between SCCs.
     */
    protected val directlyFollowsSCC: Array<IntArray> = dfSCC()

    /**
     * The eventually-follows relation between SCCs.
     */
    protected val eventuallyFollowsSCC: Array<IntArray> = efSCC()

    /**
     * All reachable SCCs from the initial state.
     */
    protected val allReachable: IntArray = allReachable()

    protected val pendingSCC = ThreadLocal.withInitial { IntHashSet() }

    companion object {
        protected const val MAX_CACHE_SIZE = 100000
    }

    protected val pendingSCCcache = LinkedHashMap<ProcessModelState, IntArray>()

    protected abstract val createPendingSCC: java.util.function.Function<ProcessModelState, IntArray>

    protected fun getPendingSCC(prevProcessState: ProcessModelState): IntArray {
        // createPendingSCC moved to a separate method to prevent allocation of lambda
        val out = pendingSCCcache.computeIfAbsent(prevProcessState, createPendingSCC)

        if (pendingSCCcache.size > MAX_CACHE_SIZE)
            pendingSCCcache.iterator().remove()

        return out
    }

    /**
     * Generates a list of strongly connected components in [model].
     * It uses Tarjan's algorithm with Nuutila's modifications - non-recursive version.
     *
     * More details:
     *  R. Tarjan (1972), Depth-first search and linear graph algorithms. SIAM Journal of Computing 1(2):146-160.
     *  E. Nuutila and E. Soisalon-Soinen (1994), On finding the strongly connected components in a directed graph. Information Processing Letters 49(1): 9-14.
     *
     *  Runs in O(|activities|)
     */
    private fun stronglyConnectedComponents(): List<Set<Activity>> {
        val stronglyConnectedComponents = LinkedList<HashSet<Activity>>()
        // Assigned to each node - the lowest node ID reachable from that node when doing a DFS (including itself)
        val lowLink = HashMap<Activity, Int>()
        // Order in normal move across graph - first element found will receive smallest number
        val preOrder = HashMap<Activity, Int>()
        // Already assigned activities
        val alreadyAssigned = HashSet<Activity>()
        // Stack - activities to analyze
        val stack = ArrayDeque<Activity>()
        // Activities waiting to assignment
        val stronglyConnectedQueue = ArrayDeque<Activity>()
        // Last label assigned
        var counter = 0

        // Analyze each activity in graph
        model.activities.forEach { source ->
            // Ignore already analyzed activities
            if (!alreadyAssigned.contains(source)) {
                // Clean stack and add current activity
                with(stack) {
                    clear()
                    add(source)
                }

                // Do-while because we have always at least one element
                do {
                    // Last element (latest added)
                    val v = stack.last()

                    // Assign label if not done before
                    if (!preOrder.containsKey(v)) {
                        // Increment counter and assign new label
                        counter++
                        preOrder[v] = counter
                    }

                    val w = model.outgoing[v]?.firstOrNull { !preOrder.containsKey(it) }
                    if (w !== null) {
                        stack.add(w)
                    } else {
                        // Assign lowLink based on preOrder - this will be maximal value which can be stored here
                        lowLink[v] = preOrder[v]!!

                        // Try to decrement value and set as minimal as possible
                        model.outgoing[v]?.forEach { w ->
                            if (w in model.activities) {
                                if (!alreadyAssigned.contains(w)) {
                                    val lowLinkV = lowLink[v]!!
                                    val preOrderedW = preOrder[w]!!

                                    if (preOrderedW > preOrder[v]!!) {
                                        lowLink[v] = min(lowLinkV, lowLink[w]!!)
                                    } else {
                                        lowLink[v] = min(lowLinkV, preOrderedW)
                                    }
                                }
                            }
                        }

                        // Remove last element from stack
                        stack.removeLast()

                        // Check - is possible to join node into group
                        if (lowLink[v] == preOrder[v]) {
                            // Create group and add `v` activity
                            val group = HashSet<Activity>().also {
                                it.add(v)
                                stronglyConnectedComponents.add(it)
                            }

                            // Check activities in queue
                            while (stronglyConnectedQueue.isNotEmpty() && preOrder[stronglyConnectedQueue.last]!! > preOrder[v]!!) {
                                group.add(stronglyConnectedQueue.removeLast())
                            }

                            // Update already assigned activities
                            alreadyAssigned.addAll(group)
                        } else {
                            // Add activity to queue - will be analyzed later
                            stronglyConnectedQueue.addLast(v)
                        }
                    }
                } while (stack.isNotEmpty())
            }
        }

        return stronglyConnectedComponents
    }

    private fun activityToSCC(): ObjectIntMap<Activity> {
        val out = ObjectIntHashMap<Activity>()
        for ((index, activities) in stronglyConnectedComponents.withIndex()) {
            for (activity in activities) {
                check(out.put(activity, index) == 0) { "Duplicate activity ${activity.name}" }
            }
        }

        return out
    }

    private fun conceptNameToSCC(): Map<String, IntArray> {
        val out = HashMap<String, IntHashSet>()
        for (cursor in activityToSCC) {
            if (!cursor.key.isSilent)
                out.computeIfAbsent(cursor.key.name) { IntHashSet() }.add(cursor.value)
        }
        return out.mapValues { (_, v) -> v.toArray() }
    }

    private fun dfSCC(): Array<IntArray> {
        val dfg = Array(stronglyConnectedComponents.size) { IntHashSet() }

        for (dep in model.directlyFollowsRelation) {
            val sourceId = activityToSCC[dep.source]
            val targetId = activityToSCC[dep.target]
            if (sourceId != targetId) {
                val row = dfg[sourceId]
                row.add(targetId)
            }
        }

        return Array(dfg.size) { dfg[it].toArray() }
    }

    private fun efSCC(): Array<IntArray> {
        fun getReachable(scc: Int): IntArray {
            val out = IntHashSet()
            val queue = IntArrayDeque()
            queue.addLast(scc)

            while (!queue.isEmpty) {
                val id = queue.removeFirst()

                if (out.add(id))
                    queue.addLast(*directlyFollowsSCC[id])
            }

            return out.toArray()
        }

        return Array(stronglyConnectedComponents.size) { index -> getReachable(index) }
    }

    private fun allReachable(): IntArray {
        val out = IntHashSet()

        for (start in model.startActivities) {
            out.addAll(*eventuallyFollowsSCC[activityToSCC[start]])
        }

        return out.toArray()
    }

}

private class DFGCausalNetWrapper(model: CausalNet) : DFGWrapper(model) {
    override val directlyFollowsRelation: Collection<CausalArc>
        get() = (model as CausalNet).dependencies
    override val outgoing: Map<Activity, Collection<Activity>> =
        model.outgoing.mapValues { (_, v) -> v.mapToSet { it.target } }
}

class CountUnmatchedLogMovesInCausalNet(model: CausalNet) : AbstractCountUnmatchedLogMoves(DFGCausalNetWrapper(model)) {

    override val createPendingSCC = java.util.function.Function<ProcessModelState, IntArray> { prevProcessState ->
        prevProcessState as CausalNetState

        if (prevProcessState.isEmpty())
            return@Function IntArray(0)

        val pendingSCC = this.pendingSCC.get()
        marking@ for (dep in prevProcessState.uniqueSet()) {
            val target = dep.target
            val targetSCC = activityToSCC[target]
            if (!pendingSCC.add(targetSCC)) {
                continue // SCC already processed
            }

            val candidates = eventuallyFollowsSCC[targetSCC]
            var index = 0
            while (index < candidates.size) {
                pendingSCC.add(candidates[index++])
            }
            if (pendingSCC.size() >= allReachable.size)
                break@marking // we have all SCCs anyway
        }

        val out = pendingSCC.toArray()
        pendingSCC.clear()
        out
    }

    override fun compute(
        startIndex: Int,
        trace: List<Event>,
        prevProcessState: ProcessModelState,
        curActivity: Activity?
    ): Int {
        prevProcessState as CausalNetState?

        val pendingSCC = getPendingSCC(prevProcessState)
        val pendingEmpty = pendingSCC.isEmpty()

        var index = startIndex
        var unmatched = 0
        mainLoop@ while (index < trace.size) {
            val conceptName = trace[index++].conceptName
            val eventSCC = conceptNameToSCC[conceptName]
            if (eventSCC === null) {
                unmatched++
                continue
            }

            var sIdx = 0
            if (pendingEmpty) {
                while (sIdx < eventSCC.size) {
                    if (allReachable.contains(eventSCC[sIdx++]))
                        continue@mainLoop
                }
            } else {
                while (sIdx < eventSCC.size) {
                    if (pendingSCC.contains(eventSCC[sIdx++]))
                        continue@mainLoop
                }
            }

            unmatched++
        }

        assert(unmatched in 0..(trace.size - startIndex))
        return unmatched
    }
}

class DFGPetriNetWrapper(model: PetriNet) : DFGWrapper(model) {
    override val directlyFollowsRelation: Collection<CausalArc> = model.places.flatMap { p ->
        val pairs = listOf(model.placeToPrecedingTransition[p].orEmpty(), model.placeToFollowingTransition[p].orEmpty())
        pairs as List<ArrayList<Transition>>
        pairs.cartesianProduct().map { Arc(it[0], it[1]) }
    }

    override val outgoing: Map<Activity, Collection<Activity>> = model.transitions.associateBy(
        { it },
        { it.outPlaces.flatMapTo(HashSet()) { model.placeToFollowingTransition[it].orEmpty() } }
    )
}

class CountUnmatchedLogMovesInPetriNet(model: PetriNet) : AbstractCountUnmatchedLogMoves(DFGPetriNetWrapper(model)) {
    private val alwaysReachable = run {
        val SCCs = IntHashSet()
        for (transition in model.transitions) {
            if (transition.inPlaces.isEmpty()) {
                SCCs.addAll(*eventuallyFollowsSCC[activityToSCC[transition]])
            }
        }
        SCCs
    }

    override val createPendingSCC = java.util.function.Function<ProcessModelState, IntArray> { prevProcessState ->
        prevProcessState as Marking
        if (prevProcessState.size == 0)
            return@Function IntArray(0)

        val pendingSCC = this.pendingSCC.get()
        places@ for (place in prevProcessState.keys) {
            val transitions = model.placeToFollowingTransition[place] ?: continue
            var tidx = 0
            while (tidx < transitions.size) {
                val transition = transitions[tidx++]
                val transitionSCC = activityToSCC[transition]
                if (!pendingSCC.add(transitionSCC)) {
                    continue // SCC already processed
                }

                val candidates = eventuallyFollowsSCC[transitionSCC]
                var index = 0
                while (index < candidates.size) {
                    pendingSCC.add(candidates[index++])
                }
                if (pendingSCC.size() >= stronglyConnectedComponents.size)
                    break@places // we have all SCCs anyway
            }
        }

        val out = pendingSCC.toArray()
        pendingSCC.clear()
        out
    }

    override fun compute(
        startIndex: Int,
        trace: List<Event>,
        prevProcessState: ProcessModelState,
        curActivity: Activity?
    ): Int {
        val pendingSCC = getPendingSCC(prevProcessState)

        var index = startIndex
        var unmatched = 0
        mainLoop@ while (index < trace.size) {
            val conceptName = trace[index++].conceptName
            val eventSCC = conceptNameToSCC[conceptName]
            if (eventSCC === null) {
                unmatched++
                continue
            }

            var sIdx = 0
            while (sIdx < eventSCC.size) {
                val scc = eventSCC[sIdx++]
                if (alwaysReachable.contains(scc))
                    continue@mainLoop

                if (pendingSCC.contains(scc))
                    continue@mainLoop
            }

            unmatched++
        }

        assert(unmatched in 0..(trace.size - startIndex))
        return unmatched
    }
}
