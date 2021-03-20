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
     * A short-hand getter for the [index]th [ActivityExecution]. See [availableActivities].
     * It is logically equivalent to `availableActivityExecutions.elementAt()` but may run faster
     * depending on implementation.
     */
    fun availableActivityExecutionAt(index: Int): ActivityExecution = availableActivityExecutions.elementAt(index)

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
