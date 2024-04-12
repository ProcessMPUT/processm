package processm.core.models.petrinet

import processm.core.models.commons.Activity
import processm.core.models.commons.ActivityExecution
import processm.core.models.commons.ProcessModelInstance
import processm.core.models.commons.ProcessModelState

/**
 * An instance of a Petri net.
 * @property model The Petri net.
 */
class PetriNetInstance(
    override val model: PetriNet
) : ProcessModelInstance {

    override var currentState: Marking = model.initialMarking.copy()
        private set

    override val availableActivities: Sequence<Activity>
        get() = model.available(currentState)

    val backwardAvailableActivities: Sequence<Transition>
        get() = model.backwardAvailable(currentState)

    override val availableActivityExecutions: Sequence<TransitionExecution>
        get() = model.available(currentState)
            .map { TransitionExecution(it, model.getCause(it, currentState), currentState) }

    override val isFinalState: Boolean
        get() = currentState == model.finalMarking

    override fun setState(state: ProcessModelState?) {
        currentState = if (state === null) model.initialMarking.copy() else state as Marking
    }

    override fun getExecutionFor(activity: Activity): ActivityExecution {
        check(model.isAvailable(activity as Transition, currentState))
        return TransitionExecution(activity, model.getCause(activity, currentState), currentState)
    }

    fun getBackwardExecutionFor(activity: Transition): ActivityExecution {
        check(activity in model.backwardAvailable(currentState))
        return BackwardTransitionExecution(activity, model.getConsequence(activity, currentState), currentState)
    }
}
