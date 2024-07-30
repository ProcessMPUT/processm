package processm.helpers

import processm.logging.logger
import java.util.*

enum class ExceptionReason {
    NotAValidURN,
    UnsupportedDatabaseForAutomaticETL,
    MissingServerName,
    MissingDatabaseName,
    MissingCollectionName,
}

/**
 * An exception supporting localization according to the remote user's locale
 *
 * @property reason The reason for the exception. While it is expected to be [ExceptionReason] or
 * [processm.services.helpers.ExceptionReason], any enum can be used as long as the string representation of its values
 * is translated in the `exceptions_*.properties` bundles
 * @property arguments Arguments for the description of the exception (a format string) retrieved from resources
 * @property message message passed to parent [Exception]. If not provided, `reason.toString()` is used.
 */
open class LocalizedException(
    val reason: Enum<*>,
    val arguments: Array<out Any?> = emptyArray(),
    message: String? = null
) : AbstractLocalizedException(message ?: reason.toString()) {

    constructor(reason: ExceptionReason, vararg arguments: Any?) : this(reason, arguments, message = null)

    override fun localizedMessage(locale: Locale): String = try {
        val formatString = getFormatString(locale, reason.toString())
        String.format(locale, formatString, *arguments)
    } catch (e: Exception) {
        logger().error("An exception was thrown while preparing localized exception", e)
        message ?: reason.toString()
    }
}
