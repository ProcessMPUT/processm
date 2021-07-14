package processm.conformance.models.footprint

import processm.core.helpers.map2d.Map2D
import processm.core.models.footprint.FootprintActivity

/**
 * Represents a difference between two [Footprint]s.
 */
data class FootprintDifference(
    val matrix: Map2D<FootprintActivity, FootprintActivity, OrderDifference>
) {
    val activities: Sequence<FootprintActivity> = matrix.rows.asSequence()
    override fun toString(): String = buildString {
        if (matrix.isEmpty())
            return@buildString

        val lengths = activities.map { it.name.length }
        val maxLength = lengths.maxOrNull() ?: 1

        append(String.format("%${maxLength}s|", ""))
        for (activity in activities) {
            append(String.format("%3s", activity))
            append('|')
        }
        append('\n')

        for (row in activities) {
            append(String.format("%${maxLength}s|", row))
            for (col in activities) {
                append(String.format("%${maxOf(col.name.length, 3)}s|", matrix[row, col] ?: ""))
            }
            append('\n')
        }
    }
}
