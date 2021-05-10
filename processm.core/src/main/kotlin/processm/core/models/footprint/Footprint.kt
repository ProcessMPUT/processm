package processm.core.models.footprint

import processm.core.helpers.map2d.Map2D
import processm.core.models.commons.ProcessModel

/**
 * A footprint matrix consisting of [Order] relations between the activities in the rows and the activities in the columns.
 * See Wil van der Aalst, Process Mining: Data Science in Action Chapters 6.2.1 and 8.4.
 *
 * @property matrix The matrix of [Order] relations.
 */
class Footprint(
    val matrix: Map2D<FootprintActivity, FootprintActivity, Order>
) : ProcessModel {
    override val activities: Sequence<FootprintActivity> = matrix.rows.asSequence()
    override val startActivities: Sequence<FootprintActivity> = matrix.columns
        .asSequence()
        .filter { activity -> matrix.getColumn(activity).values.all { it == Order.NoOrder || it == Order.PrecededBy } }
    override val endActivities: Sequence<FootprintActivity> = matrix.rows
        .asSequence()
        .filter { activity -> matrix.getRow(activity).values.all { it == Order.NoOrder || it == Order.PrecededBy } }

    override val decisionPoints: Sequence<FootprintDecisionPoint> = matrix.rows
        .asSequence().mapNotNull { act ->
            val outcomes = matrix.getRow(act).mapNotNull { (next, order) ->
                if (order == Order.FollowedBy || order == Order.Parallel) next else null
            }
            if (outcomes.size <= 1) null
            else FootprintDecisionPoint(act, outcomes)
        }

    override fun createInstance(): FootprintInstance = FootprintInstance(this)
}
