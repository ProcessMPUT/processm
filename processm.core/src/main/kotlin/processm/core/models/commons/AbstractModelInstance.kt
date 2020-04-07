package processm.core.models.commons

interface AbstractModelInstance {
    val model: AbstractModel
    val currentState: AbstractState
    val availableActivities: Sequence<AbstractActivity>
    val availableActivityExecutions: Sequence<AbstractActivityExecution>
    fun resetExecution()
}