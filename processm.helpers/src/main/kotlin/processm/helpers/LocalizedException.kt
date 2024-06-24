package processm.helpers

import processm.logging.logger
import java.util.*

enum class ExceptionReason {
    NotAValidURN,
}

class LocalizedException(val reason: ExceptionReason, val arguments: Array<out Any?>, message: String? = null) :
    AbstractLocalizedException(message ?: reason.toString()) {

    constructor(reason: ExceptionReason, vararg arguments: Any?) : this(reason, arguments)

    override fun localizedMessage(locale: Locale): String = try {
        val formatString = getFormatString(locale, reason.toString())
        String.format(locale, formatString, *arguments)
    } catch (e: Exception) {
        logger().error("An exception was thrown while preparing localized exception", e)
        message ?: reason.toString()
    }
}
