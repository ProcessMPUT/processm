package processm.miners.processtree.directlyfollowsgraph

import processm.core.log.hierarchical.Log
import processm.core.log.hierarchical.LogInputStream
import processm.core.logging.logger
import processm.core.models.processtree.Activity
import java.util.*

/**
 * Directly-follows graph based on log's events sequencess
 */
class DirectlyFollowsGraph {
    companion object {
        /**
         * Special activity SOURCE - will link to start activity in each trace
         */
        private val SOURCE_ACTIVITY = Activity("SOURCE", isSpecial = true)
        /**
         * Special activity SINK - will link to end activity in each trace
         */
        private val SINK_ACTIVITY = Activity("SINK", isSpecial = true)
    }

    /**
     * Built graph
     *
     * Structure:
     * FROM activity -> NEXT activity in each trace + arc statistic (cardinality of relations)
     *
     * Log <A, B, C> will be build as:
     *  SOURCE -> A, cardinality 1
     *  A -> B, cardinality 1
     *  B -> C, cardinality 1
     *  C -> SINK, cardinality 1
     */
    private val graph = HashMap<Activity, HashMap<Activity, Arc>>()

    /**
     * Build directly-follows graph
     */
    fun mine(log: LogInputStream): HashMap<Activity, HashMap<Activity, Arc>> {
        log.forEach { mineGraph(it) }

        // TODO: debug only
        logGraphInDotForm()

        return graph
    }

    private fun mineGraph(log: Log) {
        log.traces.forEach { trace ->
            // We are in source
            var previousActivity = SOURCE_ACTIVITY

            // Iterate over all events in current trace
            trace.events.forEach { event ->
                // TODO: we should receive activity instead of build it here
                val activity = Activity(event.conceptName!!)

                // Add connection
                addConnectionInGraph(from = previousActivity, to = activity)

                // Update previous activity
                previousActivity = activity
            }

            // Add connection with sink
            addConnectionInGraph(from = previousActivity, to = SINK_ACTIVITY)
        }
    }

    private inline fun addConnectionInGraph(from: Activity, to: Activity) {
        graph.getOrPut(from, { HashMap() })
            .compute(to) { _: Activity, value: Arc? -> (value ?: Arc()).increment() }
    }

    /**
     * Log on console graph in dot language (which we can use to prepare PNG visualisation)
     */
    private fun logGraphInDotForm() {
        logger().debug("digraph Output {")
        graph.forEach { from ->
            from.value.forEach { to ->
                logger().debug("${from.key.name} -> ${to.key.name} [label=\"${to.value.cardinality}\"]")
            }
        }
        logger().debug("}")
    }
}