package processm.core.helpers

import java.util.*

/**
 * Transforms the given [String?] to [UUID?].
 * @returns [UUID] or null if null supplied.
 * @throws IllegalArgumentException for an invalid string.
 */
fun String?.toUUID(): UUID? = this?.let { UUID.fromString(it) }
