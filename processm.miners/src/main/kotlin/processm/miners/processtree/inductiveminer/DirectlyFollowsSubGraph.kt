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
    private val outgoingConnections: Map<ProcessTreeActivity, Map<ProcessTreeActivity, Arc>>,
    /**
     * Initial connections between activities in graph (initial DFG)
     */
    private val initialConnections: Map<ProcessTreeActivity, Map<ProcessTreeActivity, Arc>> = outgoingConnections,
    /**
     * Initial start activities.
     * If not given - will be calculate based on initial connections map.
     */
    private val initialStartActivities: MutableSet<ProcessTreeActivity> = HashSet(),
    /**
     * Initial end activities.
     * If not given - will be calculate based on initial connections map.
     */
    private val initialEndActivities: MutableSet<ProcessTreeActivity> = HashSet()
) {
    companion object {
        /**
         * Zero as byte to eliminate `compareTo` in code (we have byteArrays and not be able to compare byte and Int).
         */
        private const val zeroByte: Byte = 0

        /**
         * One as byte to eliminate `compareTo` in code (we have byteArrays and not be able to compare byte and Int).
         */
        private const val oneByte: Byte = 1
    }

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

        // No initial activities assigned - we should generate assignment
        if (initialStartActivities.isEmpty()) inferStartActivities()
        if (initialEndActivities.isEmpty()) inferEndActivities()
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
    fun calculateExclusiveCut(
        outgoing: Map<ProcessTreeActivity, Map<ProcessTreeActivity, Arc>> = outgoingConnections,
        ingoing: Map<ProcessTreeActivity, Map<ProcessTreeActivity, Arc>> = ingoingConnections
    ): MutableMap<ProcessTreeActivity, Int>? {
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
                outgoing[current].orEmpty().keys.forEach { activity ->
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

                ingoing[current].orEmpty().keys.forEach { activity ->
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

            subGraphs[groupToListPosition[groupId]!!] =
                DirectlyFollowsSubGraph(
                    activities = activities,
                    outgoingConnections = connectionsHashMap,
                    initialConnections = initialConnections,
                    initialStartActivities = initialStartActivities,
                    initialEndActivities = initialEndActivities
                )
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
        // Activities waiting to assignment
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
                    if (connectionsMatrix[activityGroupID][indicatedGroupID] == zeroByte) {
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
        // Ensure at least one element in collection
        if (components.isEmpty()) {
            components.add(LinkedList<Int>())
        }
        val lastElement = components.last

        // Analyze rows of connection matrix and find
        matrix.forEachIndexed { index, group ->
            // 0 and -1 allowed here
            if (group.max() ?: 1 <= zeroByte) {
                lastElement.add(index)
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
                    if (matrix.indices.none { j -> matrix[index][j] == oneByte && j !in closedGroups }) {
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
     * Remove every dual edge and prevent loops length <= 2.
     *
     * Negated directly follows graph required for parallel cut detection
     */
    fun negateDFGConnections(): Map<ProcessTreeActivity, Map<ProcessTreeActivity, Arc>> {
        val negatedConnections = HashMap<ProcessTreeActivity, Map<ProcessTreeActivity, Arc>>()

        outgoingConnections.keys.forEach { activity ->
            negatedConnections[activity] =
                outgoingConnections[activity].orEmpty().filter { it.key !in ingoingConnections[activity].orEmpty() }
        }

        return negatedConnections
    }

    /**
     * Checks - two connected components are completely unconnected each other
     *
     * Unconnected:
     * * no connection between elements
     * * connections only in one side (no loops)
     */
    private fun isCompletelyUnConnected(
        firstGroup: Int,
        secondGroup: Int,
        assignment: Map<ProcessTreeActivity, Int>
    ): Boolean {
        val groups = componentsToGroup(assignment)

        groups[firstGroup].orEmpty().forEach { from ->
            groups[secondGroup].orEmpty().forEach { to ->
                if (ingoingConnections[from].orEmpty().containsKey(to) && ingoingConnections[to].orEmpty()
                        .containsKey(from)
                ) {
                    return false
                }
            }
        }

        // True if not found connection between activities
        return true
    }

    /**
     * Merge unconnected components into group
     *
     * Warning!
     * This function will override assignment activity to group given as a parameter `assignment` - no extra copy here to speed-up function.
     */
    private fun mergeUnConnectedComponents(assignment: MutableMap<ProcessTreeActivity, Int>) {
        // Prepare set with unique labels
        val uniqueGroupIdsSet = HashSet<Int>()
        uniqueGroupIdsSet.addAll(assignment.values)
        // Set to list + apply sort ascending
        val uniqueGroupIds = uniqueGroupIdsSet.toMutableList()
        uniqueGroupIds.sort()

        var indexFirstGroup = 0
        while (indexFirstGroup < uniqueGroupIds.size) {
            val firstGroupID = uniqueGroupIds[indexFirstGroup]
            var indexSecondGroup = indexFirstGroup + 1

            while (indexSecondGroup < uniqueGroupIds.size) {
                val secondGroupID = uniqueGroupIds[indexSecondGroup]
                if (isCompletelyUnConnected(firstGroupID, secondGroupID, assignment)) {
                    // Add group with `secondGroupID` to group with lower label value (firstGroupID)
                    assignment.forEach { if (it.value == secondGroupID) assignment[it.key] = firstGroupID }
                    // Remove group from analyze step - prevent duplicates and reassignment
                    uniqueGroupIds.removeAt(indexSecondGroup)
                    // Next iteration
                    continue
                }

                indexSecondGroup++
            }

            indexFirstGroup++
        }
    }

    private fun verifyParallelCutInAllRelations(connectedComponents: Map<ProcessTreeActivity, Int>) {
    }

    /**
     * Based on assignment activity to group prepare a hashmap
     * This will make checks simpler
     */
    private fun componentsToGroup(connectedComponents: Map<ProcessTreeActivity, Int>): Map<Int, Set<ProcessTreeActivity>> {
        val connectedComponentsGroups = HashMap<Int, HashSet<ProcessTreeActivity>>()
        connectedComponents.forEach { (activity, label) ->
            connectedComponentsGroups.getOrPut(label, { HashSet() }).add(activity)
        }

        return connectedComponentsGroups
    }

    /**
     * Validate - start and end activity in each group
     * Will be used by parallel cut check
     */
    fun isStartAndEndActivityInEachGroup(connectedComponents: Map<ProcessTreeActivity, Int>): Boolean {
        val startWithInitials = currentStartActivities().also { it.addAll(initialStartActivities) }
        val endWithInitials = currentEndActivities().also { it.addAll(initialEndActivities) }
        val connectedComponentsGroups = componentsToGroup(connectedComponents)

        connectedComponentsGroups.values.forEach { group ->
            val containsStart = (startWithInitials.firstOrNull { it in group } !== null)
            val containsEnd = (endWithInitials.firstOrNull { it in group } !== null)

            if (containsStart && containsEnd) return true
        }

        return false
    }

    /**
     * Infer start activities based on initial connection in DFG
     * This should be done only if initial start activities not assigned yet
     */
    private fun inferStartActivities() {
        // Add each activity with outgoing connection to initial start activities
        initialStartActivities.addAll(initialConnections.keys)

        val activitiesWithIngoingConnections = HashSet<ProcessTreeActivity>()
        initialConnections.values.forEach { activitiesWithIngoingConnections.addAll(it.keys) }

        // Reduce start activities - drop activity with ingoing connection
        initialStartActivities.removeAll(activitiesWithIngoingConnections)
    }

    /**
     * Infer end activities based on initial connection in DFG
     * This should be done only if initial end activities not assigned yet
     */
    private fun inferEndActivities() {
        // Add ingoing connections to initial end activities
        initialConnections.values.forEach { initialEndActivities.addAll(it.keys) }

        // Reduce end activities - drop activity with outgoing connection
        initialEndActivities.removeAll(initialConnections.keys)
    }

    /**
     * Prepare a set of activities marked as start based on initial DFG and current connections (in sub graph)
     */
    fun currentStartActivities(): MutableSet<ProcessTreeActivity> {
        val startActivities = HashSet<ProcessTreeActivity>()

        // Start activity - only ingoing connection, no one outgoing
        initialConnections.forEach { (from, toActivities) ->
            // `from` activity must not be inside activities
            if (!activities.contains(from)) {
                startActivities.addAll(toActivities.keys.filter { to -> to in activities })
            }
        }

        return startActivities
    }

    /**
     * Prepare a set of activities marked as end based on initial DFG and current connections (in sub graph)
     */
    fun currentEndActivities(): MutableSet<ProcessTreeActivity> {
        val endActivities = HashSet<ProcessTreeActivity>()

        initialConnections.forEach outside@{ (from, toActivities) ->
            if (from in activities) {
                val found = toActivities.keys.firstOrNull { it !in activities }
                if (found !== null) endActivities.add(from)
            }
        }

        return endActivities
    }

    /**
     * Detect parallel cut in directly-follows graph
     * This function with generate map with activity => label reference.
     */
    fun calculateParallelCut(): Map<ProcessTreeActivity, Int>? {
        // Negated connections in DFG required to analyze parallel cut
        val negatedOutgoingConnections = negateDFGConnections()
        val negatedIngoingConnections = HashMap<ProcessTreeActivity, HashMap<ProcessTreeActivity, Arc>>()
        negatedOutgoingConnections.forEach { (from, hashMap) ->
            hashMap.forEach { (to, arc) ->
                negatedIngoingConnections.getOrPut(to, { HashMap() })[from] = arc
            }
        }
        // Connected components in graph - THIS ASSIGNMENT WILL BE MODIFY INTERNALLY!
        val connectedComponents =
            calculateExclusiveCut(outgoing = negatedOutgoingConnections, ingoing = negatedIngoingConnections)

        // If only one unique label assigned - no assignment also here
        if (connectedComponents === null) return null

        mergeUnConnectedComponents(connectedComponents)
        verifyParallelCutInAllRelations(connectedComponents)

        // Verify each group with StartActivity and EndActivity, otherwise can not generate assignment to group
        return if (isStartAndEndActivityInEachGroup(connectedComponents)) connectedComponents else null
    }
}