package processm.core.models.commons

/**
 * An instance of a model, enabling its execution
 */
interface ProcessModelInstance {
    /**
     * The underlying model of this instance
     */
    val model: ProcessModel

    /**
     * The state of execution
     */
    val currentState: ProcessModelState

    /**
     * Activities that can be executed in [currentState]
     */
    val availableActivities: Sequence<Activity>

    /**
     * Objects to perform actual execution of [availableActivities].
     * Once execute is called on any of these objects, the remaining are invalidated and calling execute on them will, most probably, lead to an invalid state and/or an exception
     */
    val availableActivityExecutions: Sequence<ActivityExecution>

    /**
     * Resets [currentState] to the initial state to start execution from scratch
     */
    fun resetExecution()
}