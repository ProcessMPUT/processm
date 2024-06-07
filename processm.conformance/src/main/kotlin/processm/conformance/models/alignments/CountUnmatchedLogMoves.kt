package processm.conformance.models.alignments

import com.carrotsearch.hppc.IntArrayDeque
import com.carrotsearch.hppc.IntHashSet
import com.carrotsearch.hppc.ObjectIntHashMap
import com.carrotsearch.hppc.ObjectIntMap
import processm.core.log.Event
import processm.core.models.causalnet.CausalNet
import processm.core.models.causalnet.CausalNetState
import processm.core.models.commons.Activity
import processm.core.models.commons.ProcessModelState
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
    fun compute(startIndex: Int, trace: List<Event>, prevProcessState: ProcessModelState, prevActivity: Activity?): Int
}

class CountUnmatchedLogMovesInCausalNet(
    private val model: CausalNet
) : CountUnmatchedLogMoves {
    private val stronglyConnectedComponents = stronglyConnectedComponents()
    private val activityToSCC = activityToSCC()
    private val directlyFollowsSCC = dfSCC()
    private val eventuallyFollowsSCC = efSCC()
    private val allReachable = allReachable()

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

                    val w = model.outgoing[v]?.firstOrNull { !preOrder.containsKey(it.target) }?.target
                    if (w !== null) {
                        stack.add(w)
                    } else {
                        // Assign lowLink based on preOrder - this will be maximal value which can be stored here
                        lowLink[v] = preOrder[v]!!

                        // Try to decrement value and set as minimal as possible
                        model.outgoing[v]?.forEach { dep ->
                            val w = dep.target
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

    private fun activityToSCC(): ObjectIntMap<String> {
        val out = ObjectIntHashMap<String>()
        for ((index, activities) in stronglyConnectedComponents.withIndex()) {
            for (activity in activities)
                out.put(activity.name, index)
        }
        return out
    }

    private fun dfSCC(): Array<IntArray> {
        val dfg = Array(stronglyConnectedComponents.size) { IntHashSet() }

        for (dep in model.dependencies) {
            val sourceId = activityToSCC.get(dep.source.name)
            val targetId = activityToSCC.get(dep.target.name)
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

                out.add(id)
                queue.addLast(*directlyFollowsSCC[id])
            }

            return out.toArray()
        }

        return Array(stronglyConnectedComponents.size) { index -> getReachable(index) }
    }

    private fun allReachable(): IntArray {
        val out = IntHashSet()

        for (start in model.startActivities) {
            out.addAll(*eventuallyFollowsSCC[activityToSCC[start.name]])
        }

        return out.toArray()
    }

    private val pendingSCC = ThreadLocal.withInitial { IntHashSet() }

    override fun compute(
        startIndex: Int,
        trace: List<Event>,
        prevProcessState: ProcessModelState,
        prevActivity: Activity?
    ): Int {
        prevProcessState as CausalNetState?

        // event is matched if any pending obligation may fire the corresponding activity
//        val pendingActivities = prevProcessState.uniqueSet().mapToSet { it.target }
        //val pendingDeps = prevProcessState.uniqueSet().map { it.target.name }
        val pendingSCC = this.pendingSCC.get()
        var index = 0
        for (dep in prevProcessState.uniqueSet()) {
            val target = dep.target.name
            if (prevActivity != dep.target) {
                pendingSCC.add(activityToSCC[target])
            }
            val candidates = eventuallyFollowsSCC[activityToSCC[target]]
            index = 0
            while (index < candidates.size) {
                pendingSCC.add(candidates[index++])
            }
        }
        val pendingEmpty = pendingSCC.isEmpty

//        val reachableActivities =
//            pendingActivities.mapNotNullTo(HashSet()) { if (it != prevActivity) it.name else null }
//        if (pendingActivities.size == 0) {
//            reachableActivities.addAll(eventuallyFollowed[null].orEmpty())
//        } else {
//            for (activity in pendingActivities)
//                reachableActivities.addAll(eventuallyFollowed[activity.name].orEmpty())
//        }
        index = startIndex
        var unmatched = 0
        mainLoop@ while (index < trace.size) {
            val conceptName = trace[index++].conceptName
            val eventSCC = activityToSCC.getOrDefault(conceptName, -1)
            if (eventSCC < 0) {
                unmatched++
                continue
            }

//            if (conceptName != prevActivity?.name && prevProcessState.uniqueSet().any { conceptName == it.target.name })
//                continue


//            if (conceptName !in reachableActivities)
//                unmatched++


            if (pendingEmpty) {
                if (allReachable.contains(eventSCC))
                    continue
            } else {
                if (pendingSCC.contains(eventSCC))
                    continue
            }

            unmatched++
        }

        pendingSCC.clear()

        assert(unmatched in 0..(trace.size - startIndex))
        return unmatched
    }
}
