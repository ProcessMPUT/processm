package processm.miners.processtree.directlyfollowsgraph

import processm.core.log.hierarchical.Log
import processm.core.log.hierarchical.LogInputStream
import processm.core.logging.logger
import processm.core.models.processtree.Activity
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
    val graph = HashMap<Activity, HashMap<Activity, Arc>>()
    /**
     * Map with start activities (first activity in trace) + arc statistics
     *
     * Key: first activity in trace
     * Value: Statistics
     *
     * Log <A, B, C> will be build as:
     *  A, cardinality 1
     */
    val startActivities = HashMap<Activity, Arc>()
    /**
     * Map with end activities (last activity in trace) + arc statistics
     *
     * Key: last activity in trace
     * Value: Statistics
     *
     * Log <A, B, C> will be build as:
     *  C, cardinality 1
     */
    val endActivities = HashMap<Activity, Arc>()

    /**
     * Build directly-follows graph
     */
    fun mine(log: LogInputStream) {
        log.forEach { mineGraph(it) }

        // TODO: debug only
        logGraphInDotForm()
    }

    private fun mineGraph(log: Log) {
        log.traces.forEach { trace ->
            var previousActivity: Activity? = null

            // Iterate over all events in current trace
            trace.events.forEach { event ->
                // TODO: we should receive activity instead of build it here
                val activity = Activity(event.conceptName!!)

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
    private fun addConnectionInGraph(from: Activity, to: Activity) {
        graph.getOrPut(from, { HashMap() })
            .compute(to) { _: Activity, value: Arc? -> (value ?: Arc()).increment() }
    }

    /**
     * Add connection from source and first seen activity in trace.
     * Increment statistics of arc in activities map.
     */
    private fun addConnectionFromSource(activity: Activity) {
        startActivities.compute(activity) { _: Activity, value: Arc? -> (value ?: Arc()).increment() }
    }

    /**
     * Add connection from last seen activity in trace to sink.
     * Increment statistics of arc in end activities map.
     */
    private fun addConnectionToSink(activity: Activity) {
        endActivities.compute(activity) { _: Activity, value: Arc? -> (value ?: Arc()).increment() }
    }

    /**
     * Log on console graph in dot language (which we can use to prepare PNG visualisation)
     */
    private fun logGraphInDotForm() {
        logger().debug("digraph Output {")
        startActivities.forEach { start ->
            logger().debug("SOURCE -> ${start.key.name} [label=\"${start.value.cardinality}\"]")
        }
        graph.forEach { from ->
            from.value.forEach { to ->
                logger().debug("${from.key.name} -> ${to.key.name} [label=\"${to.value.cardinality}\"]")
            }
        }
        endActivities.forEach { end ->
            logger().debug("${end.key.name} -> SINK [label=\"${end.value.cardinality}\"]")
        }
        logger().debug("}")
    }
}