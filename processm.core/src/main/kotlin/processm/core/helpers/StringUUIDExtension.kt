package processm.core.helpers

import java.util.*

/**
 * Parses the given [String] as [UUID].
 * @returns [UUID] or null if null supplied.
 * @throws IllegalArgumentException for an invalid string.
 */
fun String?.toUUID(): UUID? = this?.let { UUID.fromString(it) }

/**
 * Verifies whether the [String] is in [UUID] format.
 */
fun String?.isUUID(): Boolean {
    return try {
        this.toUUID() !== null
    } catch (exception: IllegalArgumentException) {
        false
    }
}

/**
 * Attempts to transform the given object into [UUID]. Returns null and UUIDs intact; [Long]s become the least
 * significant 64-bits of [UUID], [String]s with UUID format are parsed, all other objects (including other strings)
 * are converted to string and then into a name-based [UUID].
 */
fun Any?.forceToUUID(): UUID? = when {
    this === null -> null
    this is UUID -> this
    this is Long -> UUID(0L, this)
    this is Double && this.toLong().toDouble() == this -> UUID(0L, this.toLong())
    this is String && this.isUUID() -> this.toUUID()
    else -> UUID.nameUUIDFromBytes(this.toString().toByteArray())
}
