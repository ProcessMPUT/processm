package processm.core.models.commons

/**
 * An instance of a model, enabling its execution
 */
interface AbstractModelInstance {
    /**
     * The underlying model of this instance
     */
    val model: AbstractModel

    /**
     * The state of execution
     */
    val currentState: AbstractState

    /**
     * Activities that can be executed in [currentState]
     */
    val availableActivities: Sequence<AbstractActivity>

    /**
     * Objects to perform actual execution of [availableActivities].
     * Once execute is called on any of these objects, the remaining are invalidated and calling execute on them will, most probably, lead to an invalid state and/or an exception
     */
    val availableActivityExecutions: Sequence<AbstractActivityExecution>

    /**
     * Resets [currentState] to the initial state to start execution from scratch
     */
    fun resetExecution()
}