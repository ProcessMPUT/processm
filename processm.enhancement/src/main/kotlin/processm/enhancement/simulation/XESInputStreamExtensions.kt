package processm.enhancement.simulation

import processm.core.helpers.longSum
import processm.core.helpers.map2d.DoublingMap2D
import processm.core.helpers.map2d.Map2D
import processm.core.log.Event
import processm.core.log.Log
import processm.core.log.Trace
import processm.core.log.XESInputStream

/**
 * Calculates the directly-follows graph from this event log represented as a sparse adjacency matrix with absolute
 * frequencies on arcs.
 * This method runs in O(n) time, where n is the total number of XES components in the log.
 * @exception IllegalArgumentException If an event does not have the concept:name attribute set.
 */
@Suppress("LiftReturnOrAssignment")
fun XESInputStream.getDirectlyFollowsFrequencies(): Map2D<String, String, Int> {
    val output = DoublingMap2D<String, String, Int>()
    var previous: String? = null
    for (component in this) {
        when (component) {
            is Event -> {
                requireNotNull(component.conceptName) { "The concept:name attribute must be set for all events" }
                if (previous !== null)
                    output.compute(previous, component.conceptName!!) { _, _, old -> (old ?: 0) + 1 }
                previous = component.conceptName
            }
            is Log, is Trace -> previous = null // start over
            else -> throw IllegalArgumentException("Unknown XES component $component.")
        }
    }
    return output
}

/**
 * Calculates the directly-follows graph from this event log represented as a sparse adjacency matrix with arcs labeled
 * with the conditional probabilities of running the succeeding activity directly after the preceding activity. The
 * probabilities on all arcs outgoing from the same activity sum up to 1.
 */
fun XESInputStream.getDirectlyFollowsProbabilities(): Map2D<String, String, Double> {
    val freq = getDirectlyFollowsFrequencies()
    val output = DoublingMap2D<String, String, Double>()
    for (previous in freq.rows) {
        val next = freq.getRow(previous)
        val sum = next.values.longSum()
        val outRow = output.getRow(previous)
        for ((activity, f) in next)
            outRow[activity] = f.toDouble() / sum
    }
    return output
}
