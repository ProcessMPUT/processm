package processm.core.models.commons

/**
 * An abstract process model, such as causal net, process tree, BPMN Process, Petri net
 */
interface ProcessModel {
    /**
     * All activities present in the model
     */
    val activities: Sequence<Activity>

    /**
     * All activities that can be the first one during an execution
     */
    val startActivities: Sequence<Activity>

    /**
     * All activities that can be the last one during an execution
     */
    val endActivities: Sequence<Activity>

    /**
     * Decision points, i.e., places where there may be a decision to make
     */
    val decisionPoints: Sequence<DecisionPoint>

    /**
     * Control flow structures, e.g., splits, joins, etc.
     */
    val controlStructures: Sequence<ControlStructure>

    /**
     * Returns a new instance of the model, which can then be used to execute it
     */
    fun createInstance(): ProcessModelInstance
}
