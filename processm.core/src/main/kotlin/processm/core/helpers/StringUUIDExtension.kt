package processm.core.helpers

import java.util.*

/**
 * Transforms the given [String?] to [UUID?].
 * @returns [UUID] or null if null supplied.
 * @throws IllegalArgumentException for an invalid string.
 */
fun String?.toUUID(): UUID? = this?.let { UUID.fromString(it) }

/**
 * Check String is in UUID format.
 */
fun String?.isUUID(): Boolean {
    return try {
        this.toUUID() !== null
    } catch (exception: IllegalArgumentException) {
        false
    }
}