package processm.conformance.models.alignments

import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import processm.conformance.models.DeviationType
import processm.core.log.Event
import processm.core.models.commons.Activity
import processm.core.models.commons.ProcessModelState
import java.util.*

/**
 * Represents a step in an [Alignment].
 */
@Serializable
data class Step(
    /**
     * The move in the model corresponding to this step.
     */
    val modelMove: Activity?,
    /**
     * The state of the model after conducting this step.
     */
    @Transient
    val modelState: ProcessModelState? = null,
    /**
     * The event in the log corresponding to this step. Null for no move.
     */
    val logMove: Event?,
    /**
     * The history of events after executing this step.
     */
    @Transient
    val logState: Sequence<Event>? = null,
    /**
     * The type of this step.
     */
    val type: DeviationType
) {
    override fun hashCode(): Int =
        Objects.hash(
            logMove,
            modelMove?.name,
            modelMove?.isSilent,
            modelMove?.isArtificial
        )

    override fun equals(other: Any?): Boolean =
        other is Step &&
                type == other.type &&
                logMove == other.logMove &&
                modelMove?.name == other.modelMove?.name &&
                modelMove?.isSilent == other.modelMove?.isSilent &&
                modelMove?.isArtificial == other.modelMove?.isArtificial
}
