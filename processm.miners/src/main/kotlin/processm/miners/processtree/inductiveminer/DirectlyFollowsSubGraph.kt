package processm.miners.processtree.inductiveminer

import processm.core.models.processtree.Activity
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
     * Method based on Flood fill (read more: https://en.wikipedia.org/wiki/Flood_fill)
     * This function with generate map with activity => label reference.
     */
    fun calculateExclusiveCut(): HashMap<Activity, Int> {
        // Current label
        var lastLabelId = 0

        // Activities and assigned label
        val activitiesWithLabels = HashMap<Activity, Int>()

        // Not labeled yet
        val nonLabeledActivities = HashSet<Activity>()
        activities.forEach { nonLabeledActivities.add(it) }

        // Temp list with activities to check - will be a FIFO queue
        val toCheckActivitiesListFIFO = LinkedList<Activity>()

        // Iterate until all activity receive own label
        while (nonLabeledActivities.isNotEmpty()) {
            // Add first non-labeled activity to list
            toCheckActivitiesListFIFO.addLast(nonLabeledActivities.first())
            nonLabeledActivities.remove(nonLabeledActivities.first())

            while (toCheckActivitiesListFIFO.isNotEmpty()) {
                val current = toCheckActivitiesListFIFO.first()

                // If not labeled yet - set it now
                var label = activitiesWithLabels[current]
                if (label == null) {
                    // Start new group
                    lastLabelId++
                    label = lastLabelId

                    activitiesWithLabels[current] = label
                    nonLabeledActivities.remove(current)
                    toCheckActivitiesListFIFO.add(current)
                }

                connections[current]?.keys?.forEach {
                    if (!activitiesWithLabels.containsKey(it)) {
                        activitiesWithLabels[it] = label
                        nonLabeledActivities.remove(it)
                        toCheckActivitiesListFIFO.add(it)
                    }
                }

                // Remove activity from list
                toCheckActivitiesListFIFO.removeFirst()
            }
        }

        // Return activities and assigned labels
        return activitiesWithLabels
    }
}