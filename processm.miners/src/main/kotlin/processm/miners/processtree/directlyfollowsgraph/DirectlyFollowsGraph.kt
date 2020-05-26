package processm.miners.processtree.directlyfollowsgraph

import processm.core.helpers.map2d.DoublingMap2D
import processm.core.log.hierarchical.LogInputStream
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
     * Total traces analyzed in directly-follows graph
     */
    var tracesCount = 0
        private set

    /**
     * Support activity by traces.
     * This will show you how many traces use at least one occurrence of process tree activity.
     * For log:
     * - A B C A C A
     * - A C B C D
     * - A E D
     *
     * You will receive:
     * - A: 3 (in each trace, duplicates ignored)
     * - B: 2
     * - C: 2 (duplicates ignored)
     * - D: 2
     * - E: 1
     */
    val activityTraceSupport = HashMap<ProcessTreeActivity, Int>()

    /**
     * Maximum activity occurrence in single trace after analyze all traces.
     * For log:
     * - A B C A C A
     * - A C B C C D
     * - A E D
     *
     * You will receive:
     * - A: 2 - first trace, 2x activity A
     * - B: 1 - only one B activity in traces 1st and 2nd.
     * - C: 3 - two times in first, third in second trace.
     * - D: 2 - in second and third.
     * - E: 1 - only in last trace
     */
    val maximumActivityOccurrenceInTraces = HashMap<ProcessTreeActivity, Int>()

    /**
     * Build directly-follows graph
     */
    fun discover(log: LogInputStream) = discoverGraph(log)

    /**
     * Discover changes in DFG.
     * Update internal structure of graph, analyze changes and return changes list - new connections between pair of activities.
     *
     * Can return [null] if changes in graph focused on:
     * - new start activity, or
     * - new end activity, or
     * - new activity in graph
     *
     * Empty [Set] will be if:
     * - no new connection between pair of activities in graph
     *
     * Non empty [Set] if:
     * - new connections between pair of activities. Element of [Set] == this pair of activities.
     */
    fun discoverDiff(log: LogInputStream) = discoverGraph(log, buildDiff = true)

    /**
     * Discover connections between pair of activities based on given trace.
     * If `buildDiff` - return changes in DFG.
     */
    private fun discoverGraph(
        log: LogInputStream,
        buildDiff: Boolean = false
    ): Collection<Pair<ProcessTreeActivity, ProcessTreeActivity>>? {
        var changedStartActivity = false
        var changedEndActivity = false
        var newActivityFound = false
        val addedConnectionsCollection = LinkedHashSet<Pair<ProcessTreeActivity, ProcessTreeActivity>>()

        log.forEach { l ->
            l.traces.forEach { trace ->
                val activitiesInTrace = HashMap<ProcessTreeActivity, Int>()
                var previousActivity: ProcessTreeActivity? = null

                // Iterate over all events in current trace
                trace.events.forEach { event ->
                    // TODO: we should receive activity instead of build it here
                    val activity = ProcessTreeActivity(event.conceptName!!)

                    // Update activity occurrence in trace
                    activitiesInTrace[activity] = (activitiesInTrace[activity] ?: 0) + 1

                    // Analyze new activity only if enabled diff AND no seen new activity yet.
                    // This should speed-up after found first non seen previous activity.
                    if (buildDiff && !newActivityFound) {
                        if (activity !in graph.rows && activity !in graph.columns) newActivityFound = true
                    }

                    // Add connection from source to activity
                    if (previousActivity == null) {
                        if (activity in startActivities) {
                            // Just only insert
                            startActivities[activity]!!.increment()
                        } else {
                            // Insert and increment
                            startActivities[activity] = Arc().increment()

                            // Remember - changed start activities
                            changedStartActivity = true
                        }
                    } else {
                        // Add connection between pair of activities in graph
                        with(graph[previousActivity!!, activity]) {
                            if (this == null) {
                                // If enabled diff - store new connection as pair
                                if (buildDiff) addedConnectionsCollection.add(previousActivity!! to activity)

                                graph[previousActivity!!, activity] = Arc().increment()
                            } else {
                                graph[previousActivity!!, activity] = this.increment()
                            }
                        }
                    }

                    // Update previous activity
                    previousActivity = activity
                }

                // Add connection with sink
                if (previousActivity != null) {
                    // TODO: This is really strange - previous Activity is ProcessTree Activity but compiler suggests it can be any type.
                    if (previousActivity in endActivities) {
                        // Just only insert
                        endActivities[previousActivity!!]!!.increment()
                    } else {
                        // Insert and increment
                        endActivities[previousActivity!!] = Arc().increment()

                        // Remember - changed end activity
                        changedEndActivity = true
                    }
                }

                updateTraceStatistics(activitiesInTrace)
            }
        }

        // Return diff if enabled
        return if (buildDiff) {
            if (changedStartActivity || changedEndActivity || newActivityFound) null
            else addedConnectionsCollection
        } else
            null
    }

    /**
     * Trace statistics changes:
     * - update trace support for each activity in current trace
     * - update maximum occurrence process tree activity in traces
     * - increment already analyzed traces
     */
    private fun updateTraceStatistics(activitiesInTrace: HashMap<ProcessTreeActivity, Int>) {
        // Total traces count update
        tracesCount++

        activitiesInTrace.forEach { (activity, occurrence) ->
            // If maximumActivityOccurrenceInTraces less than occurrence - update.
            // Null value with be always less than any value: https://kotlinlang.org/api/latest/jvm/stdlib/kotlin.comparisons/compare-values.html
            if (compareValues(maximumActivityOccurrenceInTraces[activity], occurrence) < 0) {
                maximumActivityOccurrenceInTraces[activity] = occurrence
            }

            // Update trace support
            activityTraceSupport[activity] = (activityTraceSupport[activity] ?: 0) + 1
        }
    }
}