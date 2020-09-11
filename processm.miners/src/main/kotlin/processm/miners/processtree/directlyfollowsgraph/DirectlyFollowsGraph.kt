package processm.miners.processtree.directlyfollowsgraph

import processm.core.helpers.map2d.DoublingMap2D
import processm.core.log.hierarchical.LogInputStream
import processm.core.models.processtree.ProcessTreeActivity
import java.util.concurrent.ConcurrentHashMap

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
     *
     * Memory usage: O(|activities|^2)
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
     *
     * Memory usage: O(|activities|)
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
     *
     * Memory usage: O(|activities|)
     */
    val endActivities = HashMap<ProcessTreeActivity, Arc>()

    /**
     * Total traces analyzed in directly-follows graph
     */
    var tracesCount = 0
        private set

    /**
     * The support of the activities by traces.
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
     *
     * Memory usage: O(|activities|)
     */
    val activityTraceSupport = HashMap<ProcessTreeActivity, Int>()

    /**
     * Duplicated activities occurrence in single trace, after analyze all traces.
     * As value number of traces where activity duplicated.
     * For log:
     * - A B C
     * - A B B C
     * - A B B C D D
     *
     * You will receive collection:
     * - B => 2 (duplicated in second trace)
     * - D => 1 (duplicated in third trace)
     *
     * Memory usage: O(|activities|)
     */
    val activitiesDuplicatedInTraces = ConcurrentHashMap<ProcessTreeActivity, Int>()

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
     * Calculate maximum trace support for activities given as input collection.
     * If activity not found in internal structure - support: 0
     *
     * Runs in: O(|collection|), maximum O(|activities|)
     */
    fun maximumTraceSupport(collection: Collection<ProcessTreeActivity>): Int {
        val activityWithHighestSupport = collection.maxBy { activityTraceSupport[it] ?: 0 }
        return activityTraceSupport[activityWithHighestSupport] ?: 0
    }

    /**
     * Discover connections between pair of activities based on given trace.
     * If `buildDiff` - return changes in DFG.
     *
     * Runs in: O(|traces| * |activities|)
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
                val activitiesInTrace = HashSet<ProcessTreeActivity>()
                val duplicatedActivities = HashSet<ProcessTreeActivity>()
                var previousActivity: ProcessTreeActivity? = null

                // Iterate over all events in current trace
                trace.events.forEach { event ->
                    // TODO: we should receive activity instead of build it here
                    val activity = ProcessTreeActivity(event.conceptName!!)

                    // Update activity occurrence in trace
                    // Runs in O(1)
                    activitiesInTrace.add(activity)

                    // Analyze new activity only if enabled diff AND no seen new activity yet.
                    // This should speed-up after found first non seen previous activity.
                    if (buildDiff && !newActivityFound) {
                        // Runs in O(1)
                        if (activity !in graph.rows && activity !in graph.columns) newActivityFound = true
                    }

                    // Add connection from source to activity
                    if (previousActivity == null) {
                        // Runs in O(1)
                        if (activity in startActivities) {
                            // Just only insert
                            // Runs in O(1)
                            startActivities[activity]!!.increment()
                        } else {
                            // Insert and increment
                            // Runs in O(1)
                            startActivities[activity] = Arc().increment()

                            // Remember - changed start activities
                            changedStartActivity = true
                        }
                    } else {
                        // If activity duplicated - remember in special collection
                        if (activity == previousActivity) {
                            // Runs in O(1)
                            duplicatedActivities.add(activity)
                        }

                        // Add connection between pair of activities in graph
                        with(graph[previousActivity!!, activity]) {
                            if (this == null) {
                                // If enabled diff - store new connection as pair
                                // Runs in O(1)
                                if (buildDiff) addedConnectionsCollection.add(previousActivity!! to activity)

                                // Runs in O(1)
                                graph[previousActivity!!, activity] = Arc().increment()
                            } else {
                                increment()
                            }
                        }
                    }

                    // Update previous activity
                    previousActivity = activity
                }

                // Add connection with sink
                if (previousActivity != null) {
                    // TODO: This is really strange - previous Activity is ProcessTree Activity but compiler suggests it can be any type.
                    // Runs in O(1)
                    if (previousActivity in endActivities) {
                        // Just only insert
                        // Runs in O(1)
                        endActivities[previousActivity!!]!!.increment()
                    } else {
                        // Insert and increment
                        // Runs in O(1)
                        endActivities[previousActivity!!] = Arc().increment()

                        // Remember - changed end activity
                        changedEndActivity = true
                    }
                }

                // Runs in O(|activities|)
                updateTraceStatistics(activitiesInTrace, duplicatedActivities)
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
     * Discover removed connections between pair of activities based on given trace.
     *
     * Runs in: O(|traces| * |activities|)
     */
    fun discoverRemovedPartOfGraph(log: LogInputStream): Collection<Pair<ProcessTreeActivity, ProcessTreeActivity>>? {
        var changedStartActivity = false
        var changedEndActivity = false
        var removedActivity = false
        val removedConnections = LinkedHashSet<Pair<ProcessTreeActivity, ProcessTreeActivity>>()

        log.forEach { l ->
            l.traces.forEach { trace ->
                // Decrement analyzed traces
                tracesCount--
                require(tracesCount >= 0) { "Cannot rollback more traces than have been previously analyzed." }

                val activitiesInTrace = HashSet<ProcessTreeActivity>()
                val duplicatedActivities = HashSet<ProcessTreeActivity>()
                var previousActivity: ProcessTreeActivity? = null

                // Iterate over all events in current trace
                trace.events.forEach { event ->
                    val activity = ProcessTreeActivity(event.conceptName!!)

                    // Update activity occurrence in trace
                    // Runs in O(1)
                    activitiesInTrace.add(activity)

                    // Connection from source to activity
                    if (previousActivity == null) {
                        require(activity in startActivities) { "The log provided for deletion has not been previously inserted into DFG." }

                        // Decrement support and remove from DFG if cardinality equal to zero
                        // Runs in O(1)
                        with(startActivities[activity]!!) {
                            decrement()

                            if (cardinality == 0) {
                                // Runs in O(1)
                                startActivities.remove(activity)
                                changedStartActivity = true
                            }
                        }
                    } else {
                        // If activity duplicated - remember in special collection
                        // Runs in O(1)
                        if (activity !in duplicatedActivities && activity == previousActivity) {
                            // Runs in O(1)
                            duplicatedActivities.add(activity)
                        }

                        // Connection between pair of activities in graph
                        // Runs in O(1)
                        with(graph[previousActivity!!, activity]) {
                            requireNotNull(this) { "Expected a path between activities $previousActivity and $activity." }

                            // Decrement support
                            decrement()

                            if (cardinality <= 0) {
                                // Runs in O(1)
                                graph.removeValue(previousActivity!!, activity)

                                // Add connection if not removed activity found
                                // If found - this collection will be ignored
                                if (!removedActivity) {
                                    // Runs in O(1)
                                    removedConnections.add(previousActivity!! to activity)
                                }
                            }
                        }
                    }

                    // Update previous activity
                    previousActivity = activity
                }

                // Add connection with sink
                if (previousActivity != null) {
                    require(previousActivity in endActivities) { "The log provided for deletion has not been previously inserted into DFG." }
                    previousActivity as ProcessTreeActivity

                    // Decrement support and remove from DFG if cardinality equal to zero
                    // Runs in O(1)
                    with(endActivities[previousActivity as ProcessTreeActivity]!!) {
                        decrement()

                        if (cardinality == 0) {
                            // Runs in O(1)
                            endActivities.remove(previousActivity as ProcessTreeActivity)
                            changedEndActivity = true
                        }
                    }
                }

                // Update trace support for each activity in current trace
                // Runs in O(|activities|)
                activitiesInTrace.forEach { activity ->
                    // Runs in O(1)
                    val support = activityTraceSupport.compute(activity) { _, v -> if (v === null) 0 else v - 1 }
                    if ((support ?: 0) <= 0) {
                        removedActivity = true
                        // Runs in O(1)
                        activityTraceSupport.remove(activity)
                        // Remove row and column with this activity
                        // Runs in O(1)
                        graph.removeColumn(activity)
                        // Runs in O(1)
                        graph.removeRow(activity)
                    }
                }

                // Update duplicates activities
                // Runs in O(|activites|)
                duplicatedActivities.forEach { activity ->
                    activitiesDuplicatedInTraces.compute(activity) { _, v ->
                        // Runs in O(1)
                        when (v) {
                            null, 1 -> activitiesDuplicatedInTraces.remove(activity)
                            else -> v - 1
                        }
                    }
                }
            }
        }

        return if (changedStartActivity || changedEndActivity || removedActivity) null
        else removedConnections
    }

    /**
     * Trace statistics changes:
     * - update trace support for each activity in current trace
     * - increment already analyzed traces
     * - update duplicated activities
     *
     * Runs in: O(|activities|)
     */
    private fun updateTraceStatistics(
        activitiesInTrace: Set<ProcessTreeActivity>,
        duplicatedActivities: HashSet<ProcessTreeActivity>
    ) {
        // Total traces count update
        tracesCount++

        activitiesInTrace.forEach { activity ->
            activityTraceSupport.compute(activity) { _, v -> if (v === null) 1 else v + 1 }
        }

        duplicatedActivities.forEach { activity ->
            activitiesDuplicatedInTraces.compute(activity) { _, v -> if (v === null) 1 else v + 1 }
        }
    }
}