package processm.conformance.models.alignments

import processm.core.log.Event
import processm.core.models.causalnet.CausalNet
import processm.core.models.causalnet.CausalNetState
import processm.core.models.commons.Activity
import processm.core.models.commons.ProcessModelState
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
    fun compute(startIndex: Int, trace: List<Event>, prevProcessState: ProcessModelState, prevActivity: Activity?): Int
}

class CountUnmatchedLogMovesInCausalNet(
    private val model: CausalNet
) : CountUnmatchedLogMoves {

    /**
     * A map from activity name to a set of eventually following activities.
     */
    private val eventuallyFollowed: Map<String?, Set<String>> = calculateEventuallyFollowed()

    private fun calculateEventuallyFollowed(): Map<String?, Set<String>> {
        val stronglyConnectedComponents = stronglyConnectedComponents()
        val activityToGroupIndex = HashMap<Activity, Int>()
        for ((index, activities) in stronglyConnectedComponents.withIndex()) {
            for (activity in activities)
                activityToGroupIndex[activity] = index
        }
        val connectionsMatrix = HashMap<Int, HashSet<Int>>()
        for (dep in model.dependencies) {
            val sourceId = activityToGroupIndex[dep.source]!!
            val targetId = activityToGroupIndex[dep.target]!!
            if (sourceId != targetId) {
                connectionsMatrix.compute(sourceId) { _, list ->
                    (list ?: HashSet()).apply { add(targetId) }
                }
            }
        }

        fun getReachableActivities(activity: Activity): Set<Activity> {
            val out = HashSet<Activity>()
            val queue = ArrayDeque<Int>()
            queue.add(activityToGroupIndex[activity]!!)

            while (queue.isNotEmpty()) {
                val id = queue.removeFirst()

                out.addAll(stronglyConnectedComponents[id])
                queue.addAll(connectionsMatrix[id].orEmpty())
            }

            return out
        }

        val out = HashMap<String?, Set<String>>()
        for (activity in model.activities) {
            out[activity.toString()] = getReachableActivities(activity).mapToSet { it.toString() }
        }

        val allReachable = HashSet<String>()
        for (node in model.startActivities) {
            allReachable.add(node.toString())
            allReachable.addAll(out[node.toString()].orEmpty())
        }
        out.put(null, allReachable)

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
        val lowLink = java.util.HashMap<Activity, Int>()
        // Order in normal move across graph - first element found will receive smallest number
        val preOrder = java.util.HashMap<Activity, Int>()
        // Already assigned activities
        val alreadyAssigned = java.util.HashSet<Activity>()
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


    override fun compute(
        startIndex: Int,
        trace: List<Event>,
        prevProcessState: ProcessModelState,
        prevActivity: Activity?
    ): Int {
        prevProcessState as CausalNetState?

        // event is matched if any pending obligation may fire the corresponding activity
        val pendingActivities = prevProcessState.uniqueSet().mapToSet { it.target }

        val reachableActivities =
            pendingActivities.mapNotNullTo(HashSet()) { if (it != prevActivity) it.toString() else null }
        if (pendingActivities.size == 0) {
            reachableActivities.addAll(eventuallyFollowed[null].orEmpty())
        } else {
            for (activity in pendingActivities)
                reachableActivities.addAll(eventuallyFollowed[activity.toString()].orEmpty())
        }
        var index = startIndex
        var unmatched = 0
        while (index < trace.size) {
            if (trace[index].conceptName !in reachableActivities)
                unmatched++
            index++
        }

        assert(unmatched in 0..(trace.size - startIndex))
        return unmatched
    }
}
