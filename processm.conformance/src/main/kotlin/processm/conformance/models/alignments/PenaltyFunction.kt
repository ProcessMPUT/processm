package processm.conformance.models.alignments

import processm.conformance.models.DeviationType

/**
 * Represents the penalties for possible steps types in an [Alignment].
 * As a general contract these values are non-negative.
 *
 * @property synchronousMove The penalty for performing the same move in the model and the log. Defaults to 0. Note that
 * some algorithms and implementations may (implicitly) assume for performance that synchronous move incurs no penalty.
 * @property silentMove The penalty for performing the silent move in the model and no move in the log. Defaults to 0.
 * Note that some algorithms and implementations may (implicitly) assume for performance that silent move incurs no penalty.
 * @property modelMove The penalty for performing a labeled move in the model and no move in the log. Defaults to 1.
 * @property logMove The penalty for performing no move in the model and a move in the log. Defaults to 1.
 */
data class PenaltyFunction(
    val synchronousMove: Int = 0,
    val silentMove: Int = 0,
    val modelMove: Int = 1,
    val logMove: Int = 1
) {
    /**
     * Calculates penalty for the given [step].
     */
    fun calculate(step: Step): Int = when (step.type) {
        DeviationType.None -> synchronousMove
        DeviationType.LogDeviation -> logMove
        DeviationType.ModelDeviation -> if (step.modelMove!!.isSilent) silentMove else modelMove
    }
}
