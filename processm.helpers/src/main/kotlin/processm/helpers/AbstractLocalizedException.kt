package processm.helpers

import processm.logging.logger
import java.util.*

/**
 * Returns the property identified by [key] from the resource bundle `exceptions`
 */
fun Locale.getErrorMessage(key: String): String = ResourceBundle.getBundle("exceptions", this).getString(key)

/**
 * A base class for exceptions supporting delayed localization, i.e., according to the preferred locale at the time when
 * the exception is handled
 */
abstract class AbstractLocalizedException(
    message: String
) : Exception(message) {

    /**
     * Reads the formatting string for the given [key] and [locale].
     *
     * @throws IllegalStateException if the given [key] is not found for the given [locale] and the fallback locale en_US.
     */
    protected fun getFormatString(locale: Locale, key: String) =
        try {
            locale.getErrorMessage(key)
        } catch (e: MissingResourceException) {
            logger().warn("Missing translation of {} to {}", key, locale)
            try {
                Locale.US.getErrorMessage(key)
            } catch (e: MissingResourceException) {
                logger().warn("Missing translation of {} to en_US (fallback)", key)
                throw IllegalStateException("Missing translation of $key to $locale.")
            }
        }

    /**
     * Returns formatted message in the specified locale
     */
    abstract fun localizedMessage(locale: Locale): String


}
