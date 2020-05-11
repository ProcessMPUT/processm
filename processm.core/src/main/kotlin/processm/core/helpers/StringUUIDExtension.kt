package processm.core.helpers

import java.util.*

/**
 * Nullable [String] to [UUID]
 */
fun String?.toUUID(): UUID? {
    try {
        if (this.isNullOrBlank()) return null
        return UUID.fromString(this)
    } catch (e: IllegalArgumentException) {
        Helpers.logger.warn("Cast to UUID not possible", e)
    }

    return null
}