package processm.core.models.petrinet

import processm.core.models.commons.Activity
import processm.core.models.commons.ProcessModelInstance
import processm.core.models.commons.ProcessModelState

/**
 * An instance of a Petri net.
 * @property model The Petri net.
 */
class PetriNetInstance(
    override val model: PetriNet
) : ProcessModelInstance {

    override var currentState: Marking = model.initialMarking
        private set

    override val availableActivities: Sequence<Activity>
        get() = model.available(currentState)

    override val availableActivityExecutions: Sequence<TransitionExecution>
        get() = model.available(currentState).map { TransitionExecution(it, currentState) }

    override val isFinalState: Boolean
        get() = currentState == model.finalMarking || availableActivities.none()

    override fun setState(state: ProcessModelState?) {
        currentState = if (state === null) model.initialMarking else state as Marking
    }
}
