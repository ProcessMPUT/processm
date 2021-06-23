package processm.core.models.footprint

import processm.core.models.commons.Activity
import processm.core.models.commons.Decision
import processm.core.models.commons.DecisionPoint

/**
 * Represents a possible decision outcome in the [Footprint] matrix.
 */
data class FootprintDecision(
    val activity: Activity,
    override val decisionPoint: DecisionPoint
) : Decision
