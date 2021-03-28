package processm.conformance.models.alignments

import processm.conformance.models.DeviationType
import processm.core.log.Event
import processm.core.models.commons.Activity
import processm.core.models.commons.ProcessModelState

data class Step(
    /**
     * The move in the model corresponding to this step.
     */
    val modelMove: Activity?,
    /**
     * The state of the model after conducting this step.
     */
    val modelState: ProcessModelState?,
    /**
     * The event in the log corresponding to this step. Null for no move.
     */
    val logMove: Event?,
    /**
     * The history of events after executing this step.
     */
    val logState: Sequence<Event>?,
    /**
     * The type of this step.
     */
    val type: DeviationType
)
