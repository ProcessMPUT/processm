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
     * A short-hand getter for the [ActivityExecution] for the given activity. The implementation must verify
     * whether the given activity is executable in the [currentState].
     * @throw IllegalStateException If the given activity is not executable in the [currentState].
     */
    fun getExecutionFor(activity: Activity): ActivityExecution =
        availableActivityExecutions.firstOrNull { it.activity == activity }
            ?: throw IllegalStateException("Activity $activity is not executable in the current state.")

    /**
     * True if the process model instance is in the final state and no activity can fire.
     */
    val isFinalState: Boolean
        get() = availableActivities.none()

    /**
     * Sets the [currentState] to the given [state]. Resets to the initial state if null is given.
     * @param state The state to set.
     */
    fun setState(state: ProcessModelState?)
}
