package processm.helpers

import processm.logging.logger
import java.util.*

fun Locale.getErrorMessage(key: String): String = ResourceBundle.getBundle("exceptions", this).getString(key)

abstract class AbstractLocalizedException(
    message: String
) : Exception(message) {

    protected fun getFormatString(locale: Locale, key: String) =
        try {
            locale.getErrorMessage(key)
        } catch (e: MissingResourceException) {
            logger().warn("Missing translation of {} to {}", key, locale)
            Locale.US.getErrorMessage(key)
        }

    /**
     * Returns formatted message in the specified locale
     */
    abstract fun localizedMessage(locale: Locale): String


}