package processm.miners.processtree.directlyfollowsgraph

import processm.core.log.hierarchical.Log
import processm.core.log.hierarchical.LogInputStream
import processm.core.models.processtree.ProcessTreeActivity
import java.util.*

/**
 * Directly-follows graph based on log's events sequences
 */
class DirectlyFollowsGraph {
    /**
     * Built graph
     *
     * Structure:
     * FROM activity -> NEXT activity in each trace + arc statistic (cardinality of relations)
     *
     * Log <A, B, B, B, C> will be build as:
     *  A -> B, cardinality 1
     *  B -> B, cardinality 2
     *  B -> C, cardinality 1
     */
    val graph = HashMap<ProcessTreeActivity, HashMap<ProcessTreeActivity, Arc>>()

    /**
     * Map with start activities (first activity in trace) + arc statistics
     *
     * Key: first activity in trace
     * Value: Statistics
     *
     * Log <A, B, C> will be build as:
     *  A, cardinality 1
     */
    val startActivities = HashMap<ProcessTreeActivity, Arc>()

    /**
     * Map with end activities (last activity in trace) + arc statistics
     *
     * Key: last activity in trace
     * Value: Statistics
     *
     * Log <A, B, C> will be build as:
     *  C, cardinality 1
     */
    val endActivities = HashMap<ProcessTreeActivity, Arc>()

    /**
     * Build directly-follows graph
     */
    fun discover(log: LogInputStream) {
        log.forEach { discoverGraph(it) }
    }

    private fun discoverGraph(log: Log) {
        log.traces.forEach { trace ->
            var previousActivity: ProcessTreeActivity? = null

            // Iterate over all events in current trace
            trace.events.forEach { event ->
                // TODO: we should receive activity instead of build it here
                val activity = ProcessTreeActivity(event.conceptName!!)

                // Add connection
                if (previousActivity == null)
                    addConnectionFromSource(activity)
                else
                    addConnectionInGraph(from = previousActivity!!, to = activity)

                // Update previous activity
                previousActivity = activity
            }

            // Add connection with sink
            if (previousActivity != null)
                addConnectionToSink(previousActivity!!)
        }
    }

    /**
     * Add connection between two activities in trace and increment statistics of arc.
     */
    private fun addConnectionInGraph(from: ProcessTreeActivity, to: ProcessTreeActivity) {
        graph.getOrPut(from, { HashMap() }).getOrPut(to, { Arc() }).increment()
    }

    /**
     * Add connection from source and first seen activity in trace.
     * Increment statistics of arc in activities map.
     */
    private fun addConnectionFromSource(activity: ProcessTreeActivity) {
        startActivities.getOrPut(activity, { Arc() }).increment()
    }

    /**
     * Add connection from last seen activity in trace to sink.
     * Increment statistics of arc in end activities map.
     */
    private fun addConnectionToSink(activity: ProcessTreeActivity) {
        endActivities.getOrPut(activity, { Arc() }).increment()
    }
}