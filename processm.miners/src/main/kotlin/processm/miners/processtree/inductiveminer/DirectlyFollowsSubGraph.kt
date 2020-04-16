package processm.miners.processtree.inductiveminer

import processm.core.models.processtree.ProcessTreeActivity
import processm.miners.processtree.directlyfollowsgraph.Arc
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
    fun calculateExclusiveCut(): Map<ProcessTreeActivity, Int> {
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

        // Return activities and assigned labels
        return activitiesWithLabels
    }

    /**
     * Split graph into subGraphs based on assignment map [ProcessTreeActivity] => [Int]
     */
    fun splitIntoSubGraphs(assignment: Map<ProcessTreeActivity, Int>): Map<Int, DirectlyFollowsSubGraph> {
        val subGraphs = HashMap<Int, DirectlyFollowsSubGraph>()
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

            subGraphs[groupId] = DirectlyFollowsSubGraph(activities, connectionsHashMap)
        }

        return subGraphs
    }
}