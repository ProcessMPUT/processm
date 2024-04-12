package processm.core.models.footprint

import processm.core.models.commons.ControlStructureType
import processm.core.models.commons.ProcessModel
import processm.helpers.asList
import processm.helpers.map2d.Map2D

/**
 * A footprint matrix consisting of [Order] relations between the activities in the rows and the activities in the columns.
 * See Wil van der Aalst, Process Mining: Data Science in Action Chapters 6.2.1 and 8.4.
 *
 * @property matrix The matrix of [Order] relations.
 */
class Footprint(
    val matrix: Map2D<FootprintActivity, FootprintActivity, Order>
) : ProcessModel {
    override val activities: List<FootprintActivity> = matrix.rows.asList()
    override val startActivities: List<FootprintActivity> = matrix.columns
        .filter { activity -> matrix.getColumn(activity).values.all { it == Order.NoOrder || it == Order.PrecededBy } }
    override val endActivities: List<FootprintActivity> = matrix.rows
        .filter { activity -> matrix.getRow(activity).values.all { it == Order.NoOrder || it == Order.PrecededBy } }

    override val decisionPoints: Sequence<FootprintDecisionPoint> = matrix.rows
        .asSequence().mapNotNull { act ->
            val outcomes = matrix.getRow(act).mapNotNull { (next, order) ->
                if (order == Order.FollowedBy || order == Order.Parallel) next else null
            }
            if (outcomes.size <= 1) null
            else FootprintDecisionPoint(act, outcomes)
        }

    override val controlStructures: Sequence<ControlStructure> = sequence {
        for (row in matrix.rows) {
            for ((col, value) in matrix.getRow(row)) {
                if (value == Order.FollowedBy || value == Order.Parallel)
                    yield(ControlStructure(ControlStructureType.OtherSplit, row, col))
            }
        }
    }

    override fun createInstance(): FootprintInstance = FootprintInstance(this)

    override fun toString(): String = buildString {
        val lengths = activities.map { it.name.length }
        val maxLength = lengths.maxOrNull() ?: 0

        append(String.format("%${maxLength}s|", ""))
        for (activity in activities) {
            append(activity)
            append('|')
        }
        append('\n')

        for (row in activities) {
            append(String.format("%${maxLength}s|", row))
            for (col in activities) {
                append(String.format("%${col.name.length}s|", matrix[row, col]?.symbol ?: ""))
            }
            append('\n')
        }
    }
}
