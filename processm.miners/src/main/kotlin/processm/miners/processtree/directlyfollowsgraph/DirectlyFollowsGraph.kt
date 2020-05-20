package processm.miners.processtree.directlyfollowsgraph

import processm.core.helpers.map2d.DoublingMap2D
import processm.core.log.hierarchical.LogInputStream
import processm.core.log.hierarchical.Trace
import processm.core.models.processtree.ProcessTreeActivity

/**
 * Directly-follows graph based on log's events sequences
 */
class DirectlyFollowsGraph {
    /**
     * Built graph
     *
     * Connection FROM -> TO stored as:
     * FROM as row index, TO as column index
     *
     * Inside [ProcessTreeActivity] [ProcessTreeActivity] position stored arc statistic (cardinality of relations)
     *
     * Log <A, B, B, B, C> will be build as:
     *    B    C
     * A  1    -
     * B  2    1
     */
    val graph = DoublingMap2D<ProcessTreeActivity, ProcessTreeActivity, Arc>()

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
        log.forEach { l ->
            l.traces.forEach { trace ->
                discoverGraph(trace, graph, startActivities, endActivities)
            }
        }
    }

    /**
     * Discover changes in DFG as diff matrix.
     * Analyze also changes in start and end activities (connections from source and into sink).
     */
    fun discoverDiff(log: LogInputStream): Triple<DoublingMap2D<ProcessTreeActivity, ProcessTreeActivity, Arc>, HashMap<ProcessTreeActivity, Arc>, HashMap<ProcessTreeActivity, Arc>> {
        val diff = DoublingMap2D<ProcessTreeActivity, ProcessTreeActivity, Arc>()
        val starts = HashMap<ProcessTreeActivity, Arc>()
        val ends = HashMap<ProcessTreeActivity, Arc>()

        log.forEach { l ->
            l.traces.forEach { trace ->
                discoverGraph(trace, diff, starts, ends)
            }
        }

        return Triple(diff, starts, ends)
    }

    /**
     * Apply calculated diff into DFG internal structure.
     * Update cardinality of arcs between activities, start and end activities.
     */
    fun applyDiff(input: Triple<DoublingMap2D<ProcessTreeActivity, ProcessTreeActivity, Arc>, HashMap<ProcessTreeActivity, Arc>, HashMap<ProcessTreeActivity, Arc>>) {
        // Update start & end activities (+ update cardinality)
        input.second.forEach { (activity, arc) -> addConnectionFromSource(activity, startActivities, arc.cardinality) }
        input.third.forEach { (activity, arc) -> addConnectionToSink(activity, endActivities, arc.cardinality) }

        // Update connections in DFG
        input.first.rows.forEach { from ->
            input.first.getRow(from).forEach { to, arc ->
                if (graph[from, to] !== null) graph[from, to]!!.increment(arc.cardinality)
                else graph[from, to] = arc
            }
        }
    }

    /**
     * Discover connections between pair of activities based on given trace.
     */
    private fun discoverGraph(
        trace: Trace,
        output: DoublingMap2D<ProcessTreeActivity, ProcessTreeActivity, Arc> = graph,
        starts: HashMap<ProcessTreeActivity, Arc> = startActivities,
        ends: HashMap<ProcessTreeActivity, Arc> = endActivities
    ) {
        var previousActivity: ProcessTreeActivity? = null

        // Iterate over all events in current trace
        trace.events.forEach { event ->
            // TODO: we should receive activity instead of build it here
            val activity = ProcessTreeActivity(event.conceptName!!)

            // Add connection
            if (previousActivity == null)
                addConnectionFromSource(activity, starts)
            else
                addConnectionInGraph(from = previousActivity!!, to = activity, output = output)

            // Update previous activity
            previousActivity = activity
        }

        // Add connection with sink
        if (previousActivity != null)
            addConnectionToSink(previousActivity!!, ends)
    }

    /**
     * Add connection between two activities in trace.
     * Increment statistics of arc already stored in graph matrix if found connection or insert new arc.
     */
    private fun addConnectionInGraph(from: ProcessTreeActivity, to: ProcessTreeActivity) {
        graph[from, to] = (graph[from, to] ?: Arc()).increment()
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