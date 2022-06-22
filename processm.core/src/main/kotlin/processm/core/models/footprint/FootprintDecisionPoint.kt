package processm.core.models.footprint

import processm.core.models.commons.Activity
import processm.core.models.commons.Decision
import processm.core.models.commons.DecisionPoint

/**
 * Represents a decision point in a [Footprint] matrix.
 */
data class FootprintDecisionPoint(
    val previousActivity: Activity,
    val nextActivities: List<Activity>,
) : DecisionPoint {

    override val previousActivities = setOf(previousActivity)
    override val possibleOutcomes: Collection<Decision> = nextActivities.map { FootprintDecision(it, this) }
}
