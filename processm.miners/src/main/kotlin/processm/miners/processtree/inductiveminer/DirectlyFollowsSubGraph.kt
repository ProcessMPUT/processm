package processm.miners.processtree.inductiveminer

import processm.core.helpers.map2d.Map2D
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
    internal val activities: Set<ProcessTreeActivity>,
    /**
     * Connections between activities in graph
     */
    private val initialConnections: Map2D<ProcessTreeActivity, ProcessTreeActivity, Arc>,
    /**
     * Initial start activities  in graph based on connections from initial DFG.
     * If not given - will be calculate based on initial connections map.
     */
    private var initialStartActivities: Set<ProcessTreeActivity>? = null,
    /**
     * Initial end activities in graph based on connections from initial DFG.
     * If not given - will be calculate based on initial connections map.
     */
    private var initialEndActivities: Set<ProcessTreeActivity>? = null
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
     * Detected cut in subGraph
     */
    lateinit var detectedCut: CutType

    /**
     * SubGraphs created based on this sub graph
     */
    lateinit var children: Array<DirectlyFollowsSubGraph?>
        private set

    /**
     * Current start activities in this subGraph.
     * Calculated once, used by parallel and loop cut detection.
     */
    private val currentStartActivities by lazy(LazyThreadSafetyMode.NONE) { currentStartActivities() }

    /**
     * Current end activities in this subGraph.
     * Calculated once, used by parallel and loop cut detection.
     */
    private val currentEndActivities by lazy(LazyThreadSafetyMode.NONE) { currentEndActivities() }

    init {
        if (initialEndActivities.isNullOrEmpty()) initialEndActivities = inferEndActivities()
        if (initialStartActivities.isNullOrEmpty()) initialStartActivities = inferStartActivities()

        detectCuts()
    }

    /**
     * Check is possible to finish calculation.
     *
     * Possible only if connections are empty (no self-loop) AND in activities only one activity.
     */
    fun canFinishCalculationsOnSubGraph(): Boolean {
        return activities.size == 1 && initialConnections[activities.first(), activities.first()] === null
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
    fun calculateExclusiveCut(): MutableMap<ProcessTreeActivity, Int>? {
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
                initialConnections.getRow(current).keys.forEach { activity ->
                    if (activity in activities) {
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

                initialConnections.getColumn(current).keys.forEach { activity ->
                    if (activity in activities) {
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
        }

        // Return assignment only if more than two groups
        return if (lastLabelId >= 2) activitiesWithLabels else null
    }

    /**
     * Split graph into subGraphs based on assignment map [ProcessTreeActivity] => [Int]
     */
    private fun splitIntoSubGraphs(assignment: Map<ProcessTreeActivity, Int>) {
        val groupToListPosition = TreeMap<Int, Int>()
        assignment.values.toSortedSet().withIndex().forEach { (index, groupId) -> groupToListPosition[groupId] = index }

        children = arrayOfNulls(size = groupToListPosition.size)
        val activityGroups = HashMap<Int, HashSet<ProcessTreeActivity>>()

        // Add each activity to designated group
        assignment.forEach { (activity, groupId) ->
            activityGroups.computeIfAbsent(groupId) { HashSet() }.add(activity)
        }

        activityGroups.forEach { (groupId, activities) ->
            children[groupToListPosition[groupId]!!] =
                DirectlyFollowsSubGraph(
                    activities = activities,
                    initialConnections = initialConnections,
                    initialStartActivities = currentStartActivities,
                    initialEndActivities = currentEndActivities
                )
        }
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

                    val w = initialConnections.getRow(v).keys.filter { it in activities }
                        .firstOrNull { !preOrder.containsKey(it) }
                    if (w !== null) {
                        stack.add(w)
                    } else {
                        // Assign lowLink based on preOrder - this will be maximal value which can be stored here
                        lowLink[v] = preOrder[v]!!

                        // Try to decrement value and set as minimal as possible
                        initialConnections.getRow(v).keys.forEach { w ->
                            if (w in activities) {
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
        initialConnections.rows.forEach { from ->
            if (from in activities) {
                val activityGroupID = activityToGroupIndex[from]!!
                initialConnections.getRow(from).keys.forEach { to ->
                    if (to in activities) {
                        val indicatedGroupID = activityToGroupIndex[to]!!

                        // Different groups
                        if (activityGroupID != indicatedGroupID) {
                            connectionsMatrix[indicatedGroupID][activityGroupID] = 1
                            connectionsMatrix[activityGroupID][indicatedGroupID] = -1
                        }
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
        var previousGroup = HashSet<Int>()
        // Ensure at least one element in collection
        if (components.isEmpty()) {
            components.add(LinkedList<Int>())
        }

        // Analyze rows of connection matrix and find
        matrix.forEachIndexed { index, group ->
            // 0 and -1 allowed here
            if (group.max() ?: 1 <= zeroByte) {
                components.last.add(index)
                closedGroups.add(index)
                previousGroup.add(index)
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
                        closedGroups.add(index)

                        // Decide where add element - if one of previous group without connection
                        // we will need to merge components
                        if (previousGroup.all { j -> matrix[index][j] == oneByte }) {
                            currentIterationComponents.add(index)
                        } else {
                            components.last.add(index)
                        }
                    }
                }
            }

            if (currentIterationComponents.isNotEmpty()) {
                continueAnalyze = true
                components.add(currentIterationComponents)
            }

            // Update previous added group indexes
            previousGroup = components.last.toHashSet()
        }

        var notAddYet = true
        matrix.forEachIndexed { index, _ ->
            if (!closedGroups.contains(index)) {
                if (notAddYet) {
                    notAddYet = false
                    components.add(LinkedList())
                }
                components.last.add(index)
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
     * Based on assignment activity to group prepare a hashmap
     * This will make checks simpler
     */
    private fun componentsToGroup(connectedComponents: Map<ProcessTreeActivity, Int>): HashMap<Int, HashSet<ProcessTreeActivity>> {
        val connectedComponentsGroups = HashMap<Int, HashSet<ProcessTreeActivity>>()
        connectedComponents.forEach { (activity, label) ->
            connectedComponentsGroups.getOrPut(label, { HashSet() }).add(activity)
        }

        return connectedComponentsGroups
    }

    /**
     * Validate - start and end activity in each group
     * Apply reassignment inside function => create component assignment.
     */
    private fun startAndEndActivityInEachReassignment(connectedComponents: MutableMap<ProcessTreeActivity, Int>): MutableMap<ProcessTreeActivity, Int>? {
        val connectedComponentsGroups = componentsToGroup(connectedComponents)

        val componentsWithStartEnd = LinkedList<MutableSet<ProcessTreeActivity>>()
        val componentsWithEndOnly = LinkedList<MutableSet<ProcessTreeActivity>>()
        val componentsWithStartOnly = LinkedList<MutableSet<ProcessTreeActivity>>()
        val componentsWithNothing = LinkedList<MutableSet<ProcessTreeActivity>>()

        // Analyze each group and create component assignment to one of 4 categories
        connectedComponentsGroups.values.forEach { group ->
            val containsStart = (currentStartActivities.firstOrNull { it in group } !== null)
            val containsEnd = (currentEndActivities.firstOrNull { it in group } !== null)

            when (containsStart) {
                true -> when (containsEnd) {
                    true -> componentsWithStartEnd.add(group)
                    false -> componentsWithStartOnly.add(group)
                }
                false -> when (containsEnd) {
                    true -> componentsWithEndOnly.add(group)
                    false -> componentsWithNothing.add(group)
                }
            }
        }

        // We need at least one component with both start and end activities
        if (componentsWithStartEnd.isEmpty()) return null

        var startCounter = 0
        var endCounter = 0
        val outputComponents = ArrayList(componentsWithStartEnd)
        while (startCounter < componentsWithStartOnly.size && endCounter < componentsWithEndOnly.size) {
            HashSet<ProcessTreeActivity>(componentsWithStartOnly[startCounter]).also {
                it.addAll(componentsWithEndOnly[endCounter])
                outputComponents.add(it)
            }

            startCounter++
            endCounter++
        }

        val firstOutputElement = outputComponents[0]
        // The start-only components can be added to any set
        while (startCounter < componentsWithStartOnly.size) {
            firstOutputElement.addAll(componentsWithStartOnly[startCounter])
            startCounter++
        }

        // The end-only components can be added to any set
        while (endCounter < componentsWithEndOnly.size) {
            firstOutputElement.addAll(componentsWithEndOnly[endCounter])
            endCounter++
        }

        // The non-start-non-end components can be added to any set
        for (group in componentsWithNothing) {
            firstOutputElement.addAll(group)
        }

        // Prepare assignment activity to group required by split function
        val assignment = HashMap<ProcessTreeActivity, Int>()
        outputComponents.forEachIndexed { index, activities ->
            activities.forEach { assignment[it] = index }
        }

        return if (outputComponents.size >= 2) assignment else null
    }

    /**
     * Infer start activities based on initial connection in DFG
     * This should be done only if initial start activities not assigned yet
     *
     * Columns contains all activities with INGOING connection.
     * Single row for each activity (OUTGOING connections).
     * Rows minus columns == activities without ingoing connections.
     */
    private fun inferStartActivities(): Set<ProcessTreeActivity>? {
        return initialConnections.rows.minus(initialConnections.columns)
    }

    /**
     * Infer end activities based on initial connection in DFG
     * This should be done only if initial end activities not assigned yet
     *
     * Columns contains all activities with INGOING connection.
     * Single row for each activity (OUTGOING connections).
     * Columns minus rows == activities without outgoing connections.
     */
    private fun inferEndActivities(): Set<ProcessTreeActivity> {
        return initialConnections.columns.minus(initialConnections.rows)
    }

    /**
     * Prepare a set of activities marked as start based on initial DFG and current connections (in sub graph)
     */
    fun currentStartActivities(): MutableSet<ProcessTreeActivity> {
        var collection = HashSet<ProcessTreeActivity>(initialStartActivities!!)

        for (_i in 0..activities.size) {
            collection.filter { it in activities }.also {
                // If at least one start activity still in graph - return start activities
                if (it.isNotEmpty()) return it.toMutableSet()
            }

            // Else we should find activities connected to current StartActivities
            val startActivities = HashSet<ProcessTreeActivity>()
            collection.forEach { start ->
                startActivities.addAll(initialConnections.getRow(start).keys)
            }

            collection = startActivities
        }

        // Return empty set - not recognized start activities
        return mutableSetOf()
    }

    /**
     * Prepare a set of activities marked as end based on initial DFG and current connections (in sub graph)
     */
    fun currentEndActivities(): MutableSet<ProcessTreeActivity> {
        var collection = HashSet<ProcessTreeActivity>(initialEndActivities!!)

        for (_i in 0..activities.size) {
            collection.filter { it in activities }.also {
                // If at least one end activity still in graph - return start activities
                if (it.isNotEmpty()) return it.toMutableSet()
            }

            // Else we should find activities connected to current EndActivities
            val endActivities = HashSet<ProcessTreeActivity>()
            initialConnections.rows.forEach { from ->
                if (initialConnections.getRow(from).keys.firstOrNull { it in collection } !== null) {
                    endActivities.add(from)
                }
            }

            collection = endActivities
        }

        // Return empty set - not recognized end activities
        return mutableSetOf()
    }

    /**
     * Merge two components - second component will be kept.
     */
    private fun mergeComponents(
        a1: ProcessTreeActivity,
        a2: ProcessTreeActivity,
        components: MutableMap<ProcessTreeActivity, Int>
    ) {
        val fromLabel = components[a1]!!
        val toLabel = components[a2]!!
        for (c in components) {
            if (c.value == fromLabel) c.setValue(toLabel)
        }
    }

    /**
     * Detect parallel cut in directly-follows graph
     * This function with generate map with activity => label reference.
     */
    fun calculateParallelCut(): Map<ProcessTreeActivity, Int>? {
        // Initialise each activity as a component
        val components = HashMap<ProcessTreeActivity, Int>()
        activities.withIndex().forEach { components[it.value] = it.index }

        // Walk through all possible edges
        // If an edge is missing, then the source and target cannot be in different components
        for (a1 in activities) {
            for (a2 in activities) {
                if (components[a1] != components[a2]) {
                    if (initialConnections[a1, a2] === null || initialConnections[a2, a1] === null) {
                        mergeComponents(a1, a2, components)
                    }
                }
            }
        }

        // Verify each group with StartActivity and EndActivity, otherwise can not generate assignment to group
        // Re-assignment applied inside function
        return startAndEndActivityInEachReassignment(components)
    }

    /**
     * Detect loop-cut in graph.
     *
     * Temporarily removing the start and end activities (connection from this activities to each non start/end activity)
     * and computing the connected components.
     *
     * In the resulting graph roughly gives the loop cut.
     */
    private fun calculateLoopCut(): Map<ProcessTreeActivity, Int>? {
        // Without start / end we can't generate loop
        if (currentStartActivities.isEmpty() || currentEndActivities.isEmpty()) return null

        // Activities to components, each activity in own component
        val components = HashMap<ProcessTreeActivity, Int>()
        activities.withIndex().forEach { components[it.value] = it.index }

        // Merge all start and end activities into one component
        components[currentStartActivities.first()]!!.also { groupId ->
            currentStartActivities.forEach { components[it] = groupId }
            currentEndActivities.forEach { components[it] = groupId }
        }

        // Merge the other connected components
        initialConnections.rows.forEach { source ->
            if (source in activities) {
                initialConnections.getRow(source).keys.forEach { target ->
                    if (target in activities) {
                        if (source !in currentEndActivities) {
                            if (source in currentStartActivities) {
                                // A redo cannot be reachable from a start activity that is not an end activity
                                mergeComponents(source, target, components)
                            } else {
                                // This is an edge inside a sub-component
                                if (target !in currentStartActivities) mergeComponents(source, target, components)
                            }
                        }
                    }
                }
            }
        }

        // We have merged all sub-components. We only have to find out whether each sub-component belongs to the body or the redo.
        // Make a list of sub-start and sub-end activities
        val subStartActivities = HashSet<ProcessTreeActivity>()
        val subEndActivities = HashSet<ProcessTreeActivity>()
        initialConnections.rows.forEach { source ->
            if (source in activities) {
                initialConnections.getRow(source).keys.forEach { target ->
                    if (target in activities) {
                        if (components[source] != components[target]) {
                            subEndActivities.add(source)
                            subStartActivities.add(target)
                        }
                    }
                }
            }
        }

        // A sub-end activity of a redo should have connections to all start activities
        for (subEndActivity in subEndActivities) {
            for (startActivity in currentStartActivities) {
                if (components[subEndActivity] == components[startActivity]) {
                    // subEndActivity is already in the body
                    break
                }
                if (initialConnections[subEndActivity, startActivity] === null) {
                    mergeComponents(subEndActivity, startActivity, components)
                    break
                }
            }
        }

        // A sub-start activity of a redo should be connections from all end activities
        for (subStartActivity in subStartActivities) {
            for (endActivity in currentEndActivities) {
                if (components[subStartActivity] == components[endActivity]) {
                    // subStartActivity is already in the body
                    break
                }
                if (initialConnections[endActivity, subStartActivity] === null) {
                    mergeComponents(subStartActivity, endActivity, components)
                    break
                }
            }
        }

        // Put the start and end activity component first
        val startLabel = components[currentStartActivities.first()]!!
        // Normally we have indexes 0, 1, 2...
        // If start group not first element - set as -1 to be first in ordered list
        if (startLabel > 0) {
            for (c in components) {
                if (c.value == startLabel) c.setValue(-1)
            }
        }

        return if (components.values.toSet().size >= 2) components else null
    }

    /**
     * Detect cuts in graph
     */
    private fun detectCuts() {
        if (canFinishCalculationsOnSubGraph()) {
            detectedCut = CutType.Activity
            return
        }

        // Try to perform exclusive cut
        val connectedComponents = calculateExclusiveCut()
        if (connectedComponents !== null) {
            detectedCut = CutType.Exclusive
            return splitIntoSubGraphs(connectedComponents)
        }

        // Sequence cut
        val stronglyConnectedComponents = stronglyConnectedComponents()
        val seqAssignment = calculateSequentialCut(stronglyConnectedComponents)
        if (seqAssignment !== null) {
            detectedCut = CutType.Sequence
            return splitIntoSubGraphs(seqAssignment)
        }

        // Parallel cut
        val parallelAssignment = calculateParallelCut()
        if (parallelAssignment !== null) {
            detectedCut = CutType.Parallel
            return splitIntoSubGraphs(parallelAssignment)
        }

        // Redo-loop cut
        val loopAssignment = calculateLoopCut()
        if (loopAssignment !== null) {
            detectedCut = CutType.RedoLoop
            return splitIntoSubGraphs(loopAssignment)
        }

        // Flower model - default cut
        detectedCut = CutType.FlowerModel
    }

    /**
     * Rebuild subGraph
     * This is simple wrapper on detectCuts function.
     */
    fun rebuild() {
        detectCuts()
    }
}