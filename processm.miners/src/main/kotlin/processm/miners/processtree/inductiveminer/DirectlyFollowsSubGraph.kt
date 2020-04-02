package processm.miners.processtree.inductiveminer

import processm.core.models.processtree.Activity
import processm.core.models.processtree.SilentActivity
import processm.miners.processtree.directlyfollowsgraph.Arc
import java.util.*
import kotlin.collections.HashMap
import kotlin.collections.HashSet

class DirectlyFollowsSubGraph(
    /**
     * Activities in directly-follows subGraph
     */
    private val activities: HashSet<Activity>,
    /**
     * Connections between activities in graph
     */
    private val connections: HashMap<Activity, HashMap<Activity, Arc>>
) {
    /**
     * Check is possible to finish calculation.
     *
     * Possible only if connections are empty (no self-loop) AND in activities ZERO or ONE activity.
     */
    fun canFinishCalculationsOnSubGraph(): Boolean {
        return connections.isEmpty() && activities.size <= 1
    }

    /**
     * Finish calculations and return activity
     */
    fun finishCalculations(): Activity {
        // Check can finish condition
        if (!canFinishCalculationsOnSubGraph())
            throw IllegalStateException("SubGraph is not split yet. Can't fetch activity!")

        // Empty graph - return silent activity
        if (activities.isEmpty()) return SilentActivity()

        // Return activity
        return activities.first()
    }

    /**
     * Method based on Flood fill (read more: https://en.wikipedia.org/wiki/Flood_fill)
     * This function with generate map with activity => label reference.
     */
    fun calculateExclusiveCut(): HashMap<Activity, Int> {
        // Last assigned label, on start 0 (not assigned yet)
        var lastLabelId = 0

        // Activities and assigned label
        val activitiesWithLabels = HashMap<Activity, Int>()

        // Not labeled yet activities
        val nonLabeledActivities = HashSet<Activity>()

        // Add each activity on graph
        activities.forEach { nonLabeledActivities.add(it) }

        // Temp list with activities to check - will be a FIFO queue
        val toCheckActivitiesListFIFO = LinkedList<Activity>()

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

                // Iterate over not labeled yet activities
                val iter = nonLabeledActivities.iterator()
                while (iter.hasNext()) {
                    val activity = iter.next()

                    if (areConnected(current, activity)) {
                        // Assign label
                        activitiesWithLabels[activity] = label

                        // Add activity to check list
                        toCheckActivitiesListFIFO.add(activity)

                        // Remove activity from not labeled yet activities list
                        iter.remove()
                    }
                }
            }
        }

        // Return activities and assigned labels
        return activitiesWithLabels
    }

    /**
     * Check in connections exists `from` -> `to` or `to` -> `from` connection
     */
    private fun areConnected(from: Activity?, to: Activity): Boolean {
        return (connections.containsKey(from) && connections[from]!!.containsKey(to)) ||
                (connections.containsKey(to) && connections[to]!!.containsKey(from))
    }
}