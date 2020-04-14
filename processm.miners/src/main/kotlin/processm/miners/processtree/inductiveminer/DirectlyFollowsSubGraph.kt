package processm.miners.processtree.inductiveminer

import processm.core.models.processtree.ProcessTreeActivity
import processm.core.models.processtree.RedoLoop
import processm.core.models.processtree.SilentActivity
import processm.miners.processtree.directlyfollowsgraph.Arc
import java.lang.Integer.min
import java.util.*
import kotlin.collections.HashMap
import kotlin.collections.HashSet

class DirectlyFollowsSubGraph(
    /**
     * Activities in directly-follows subGraph
     */
    private val activities: Set<ProcessTreeActivity>,
    /**
     * Connections between activities in graph
     * Outgoing - `key` activity has reference to activities which it directly points to.
     */
    private val outgoingConnections: Map<ProcessTreeActivity, Map<ProcessTreeActivity, Arc>>
) {
    /**
     * Activities pointed (with connection) to `key` activity
     */
    private val ingoingConnections = HashMap<ProcessTreeActivity, HashMap<ProcessTreeActivity, Arc>>()

    init {
        outgoingConnections.forEach { (from, hashMap) ->
            hashMap.forEach { (to, arc) ->
                ingoingConnections.getOrPut(to, { HashMap() })[from] = arc
            }
        }
    }

    /**
     * Check is possible to finish calculation.
     *
     * Possible only if connections are empty (no self-loop) AND in activities only one activity.
     */
    fun canFinishCalculationsOnSubGraph(): Boolean {
        return outgoingConnections.isEmpty() && activities.size == 1
    }

    /**
     * Finish calculations and return activity
     */
    fun finishCalculations(): ProcessTreeActivity {
        // Check can finish condition
        check(canFinishCalculationsOnSubGraph()) { "SubGraph is not split yet. Can't fetch activity!" }

        // Return activity
        return activities.first()
    }

    /**
     * Method based on Flood fill (read more: https://en.wikipedia.org/wiki/Flood_fill)
     * Each activity will receive label - we want to assign a number as low as possible.
     * Based on assigned label activities merged into groups.
     *
     * This function generates a map of [ProcessTreeActivity] => [Int] label reference.
     */
    fun calculateExclusiveCut(): Map<ProcessTreeActivity, Int>? {
        // Last assigned label, on start 0 (not assigned yet)
        var lastLabelId = 0

        // Activities and assigned label
        val activitiesWithLabels = HashMap<ProcessTreeActivity, Int>()

        // Not labeled yet activities
        val nonLabeledActivities = HashSet<ProcessTreeActivity>(activities)

        // Temp list with activities to check - will be a FIFO queue
        val toCheckActivitiesListFIFO = LinkedList<ProcessTreeActivity>()

        // Iterate until all activity receive own label
        while (nonLabeledActivities.isNotEmpty()) {
            // Add first non-labeled activity to list
            toCheckActivitiesListFIFO.addLast(nonLabeledActivities.first())

            while (toCheckActivitiesListFIFO.isNotEmpty()) {
                // Now we will analise this activity
                val current = toCheckActivitiesListFIFO.pop()

                // Get activity label
                var label = activitiesWithLabels[current]

                // If not labeled yet - start new group
                if (label == null) {
                    // Start new group
                    lastLabelId++
                    label = lastLabelId

                    // Assign label and remove from not labeled yet activities
                    activitiesWithLabels[current] = label
                    nonLabeledActivities.remove(current)
                }

                // Iterate over activities connected with my `current` (current -> activity)
                outgoingConnections[current].orEmpty().keys.forEach { activity ->
                    // If not assigned label yet
                    if (nonLabeledActivities.contains(activity)) {
                        // Assign label
                        activitiesWithLabels[activity] = label
                        // Add activity to check list
                        toCheckActivitiesListFIFO.add(activity)
                        // Remove activity from not labeled yet activities list
                        nonLabeledActivities.remove(activity)
                    }
                }

                ingoingConnections[current].orEmpty().keys.forEach { activity ->
                    // If not assigned label yet
                    if (nonLabeledActivities.contains(activity)) {
                        // Assign label
                        activitiesWithLabels[activity] = label
                        // Add activity to check list
                        toCheckActivitiesListFIFO.add(activity)
                        // Remove activity from not labeled yet activities list
                        nonLabeledActivities.remove(activity)
                    }
                }
            }
        }

        // Return assignment only if more than two groups
        return if (lastLabelId >= 2) activitiesWithLabels else null
    }

    /**
     * Split graph into subGraphs based on assignment map [ProcessTreeActivity] => [Int]
     */
    fun splitIntoSubGraphs(assignment: Map<ProcessTreeActivity, Int>): Array<DirectlyFollowsSubGraph?> {
        val groupToListPosition = TreeMap<Int, Int>()
        assignment.values.toSortedSet().withIndex().forEach { (index, groupId) -> groupToListPosition[groupId] = index }

        val subGraphs = arrayOfNulls<DirectlyFollowsSubGraph>(size = groupToListPosition.size)
        val activityGroups = HashMap<Int, HashSet<ProcessTreeActivity>>()

        // Add each activity to designated group
        assignment.forEach { (activity, groupId) ->
            activityGroups.getOrPut(groupId, { HashSet() }).add(activity)
        }

        activityGroups.forEach { (groupId, activities) ->
            // Prepare connections map
            val connectionsHashMap = HashMap<ProcessTreeActivity, Map<ProcessTreeActivity, Arc>>()

            // For each activity add connection with another activities from group
            activities.forEach { activity ->
                connectionsHashMap[activity] = outgoingConnections[activity].orEmpty().filter { it.key in activities }
            }

            subGraphs[groupToListPosition[groupId]!!] = DirectlyFollowsSubGraph(activities, connectionsHashMap)
        }

        return subGraphs
    }

    /**
     * Default rule for Inductive Miner - generate redo loop with silent activity and each activity in graph:
     * ⟲(τ, a1, a2, ..., an)
     */
    fun finishWithDefaultRule(): RedoLoop {
        val listOfActivities = arrayOfNulls<ProcessTreeActivity>(size = activities.size + 1)

        // Add silent activity as first element
        listOfActivities[0] = SilentActivity()

        // Add activities
        activities.withIndex().forEach { (index, activity) ->
            listOfActivities[index + 1] = activity
        }

        // Prepare node with redo-loop operator
        return RedoLoop(*listOfActivities.requireNoNulls())
    }

    /**
     * Generate a list of strongly connected components in DFG.
     * Uses Tarjan's algorithm with Nuutila's modifications - non recursive version.
     *
     * More details:
     *  R. Tarjan (1972), Depth-first search and linear graph algorithms. SIAM Journal of Computing 1(2):146-160.
     *  E. Nuutila and E. Soisalon-Soinen (1994), On finding the strongly connected components in a directed graph. Information Processing Letters 49(1): 9-14.
     */
    fun stronglyConnectedComponents(): List<Set<ProcessTreeActivity>> {
        val stronglyConnectedComponents = LinkedList<HashSet<ProcessTreeActivity>>()
        // Assigned to each node - the lowest node ID reachable from that node when doing a DFS (including itself)
        val lowLink = HashMap<ProcessTreeActivity, Int>()
        // Order in normal move across graph - first element found will receive smallest number
        val preOrder = HashMap<ProcessTreeActivity, Int>()
        // Already assigned activities
        val alreadyAssigned = HashSet<ProcessTreeActivity>()
        // Stack - activities to analyze
        val stack = ArrayDeque<ProcessTreeActivity>()
        // Activities waiting to assigment
        val stronglyConnectedQueue = ArrayDeque<ProcessTreeActivity>()
        // Last label assigned
        var counter = 0

        // Analyze each activity in graph
        activities.forEach { source ->
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

                    val w = outgoingConnections[v]?.keys?.firstOrNull { !preOrder.containsKey(it) }
                    if (w !== null) {
                        stack.add(w)
                    } else {
                        // Assign lowLink based on preOrder - this will be maximal value which can be stored here
                        lowLink[v] = preOrder[v]!!

                        // Try to decrement value and set as minimal as possible
                        outgoingConnections[v].orEmpty().keys.forEach { w ->
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

                        // Remove last element from stack
                        stack.removeLast()

                        // Check - is possible to join node into group
                        if (lowLink[v] == preOrder[v]) {
                            // Create group and add `v` activity
                            val group = HashSet<ProcessTreeActivity>().also {
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

    /**
     * Prepare the connection matrix between strongly connected components
     * It returns a group_id x group_id matrix, where an element in ith row and jth column
     * indicates that reference between groups.
     */
    fun connectionMatrix(stronglyConnectedComponents: List<Set<ProcessTreeActivity>>): Array<ByteArray> {
        // Mapping activity -> group ID
        val activityToGroupIndex = HashMap<ProcessTreeActivity, Int>()
        // Assign group ID to activity
        stronglyConnectedComponents.forEachIndexed { index, elements ->
            elements.forEach { activity ->
                activityToGroupIndex[activity] = index
            }
        }

        // Prepare matrix with connections between groups
        val size = stronglyConnectedComponents.size
        val connectionsMatrix = Array(size) { ByteArray(size) }

        // Iterate over connections in graph
        outgoingConnections.forEach { connection ->
            val activityGroupID = activityToGroupIndex[connection.key]!!
            connection.value.forEach {
                val indicatedGroupID = activityToGroupIndex[it.key]!!

                // Different groups
                if (activityGroupID != indicatedGroupID) {
                    connectionsMatrix[indicatedGroupID][activityGroupID] = 1

                    // Reversed connection check - if not stored yet - set -1 in matrix
                    if ((connectionsMatrix[activityGroupID][indicatedGroupID]).compareTo(0) == 0) {
                        connectionsMatrix[activityGroupID][indicatedGroupID] = -1
                    }
                }
            }
        }

        return connectionsMatrix
    }

    /**
     * Detect sequential cut in directly-follows graph
     *
     * This function generates a map of [ProcessTreeActivity] => [Int] label reference.
     */
    fun calculateSequentialCut(stronglyConnectedComponents: List<Set<ProcessTreeActivity>>): Map<ProcessTreeActivity, Int>? {
        // This makes sense only if more than one strongly connected component
        if (stronglyConnectedComponents.size <= 1) return null

        // Activities and assigned label
        val activitiesWithLabels = HashMap<ProcessTreeActivity, Int>()
        // Connection matrix between components in graph
        val matrix = connectionMatrix(stronglyConnectedComponents)
        // List with components - we will manipulate it
        val components = LinkedList<LinkedList<Int>>()
        // Groups already analyzed
        val closedGroups = HashSet<Int>()

        // Analyze rows of connection matrix and find
        // After this block we expect to receive list with zero or one element
        // If one element - contain all groups (indexes) with max value == 0
        matrix.forEachIndexed { index, group ->
            // 0 and -1 allowed here
            if (group.max()?.compareTo(0) ?: 1 == 0) {
                if (components.isEmpty()) {
                    components.add(LinkedList<Int>())
                }
                components.last().add(index)
                closedGroups.add(index)
            }
        }

        // Analyze each component
        var continueAnalyze = components.isNotEmpty()
        while (continueAnalyze) {
            continueAnalyze = false
            val currentIterationComponents = LinkedList<Int>()

            matrix.forEachIndexed { index, _ ->
                // If group not closed
                if (!closedGroups.contains(index)) {
                    val temp = HashSet<Int>()
                    matrix.forEachIndexed { j, _ ->
                        if (matrix[index][j].compareTo(1) == 0) {
                            temp.add(j)
                        }
                    }
                    if (temp.minus(closedGroups).isEmpty()) {
                        currentIterationComponents.add(index)
                        closedGroups.add(index)
                    }
                }
            }

            if (currentIterationComponents.isNotEmpty()) {
                continueAnalyze = true
                components.add(currentIterationComponents)
            }
        }

        var notAddYet = true
        matrix.forEachIndexed { index, _ ->
            if (!closedGroups.contains(index)) {
                if (notAddYet) {
                    notAddYet = false
                    components.add(LinkedList())
                }
                components.last().add(index)
            }
        }

        // Analyze prepared components and build response
        if (components.size > 1) {
            var labelGroup = 1
            components.forEach { group ->
                group.forEach { index ->
                    stronglyConnectedComponents[index].forEach { activity ->
                        activitiesWithLabels[activity] = labelGroup
                    }
                }

                // Increment group ID
                labelGroup++
            }

            return activitiesWithLabels
        }

        // No assignment
        return null
    }

    /**
     * Based on DFG prepare negated DFG
     * Remove every dual edge, and add double edges where there was no or a single edge present.
     *
     * Negated directly follows graph required for parallel cut detection
     */
    fun negateDFGConnections(): Map<ProcessTreeActivity, Map<ProcessTreeActivity, Arc>> {
        val negatedConnections = HashMap<ProcessTreeActivity, HashMap<ProcessTreeActivity, Arc>>()

        outgoingConnections.forEach { (activity, group) ->
            group.forEach { (to, arc) ->
                val containsLoopConnection = ingoingConnections[activity]?.containsKey(to) ?: false
                if (!containsLoopConnection) {
                    negatedConnections.getOrPut(activity, { HashMap() })[to] = arc
                }
            }
        }

        return negatedConnections
    }
}