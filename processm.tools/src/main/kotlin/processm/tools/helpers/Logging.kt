package processm.tools.helpers

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import kotlin.reflect.full.companionObject

/**
 * Determines the right class to initialize the logger for.
 */
fun <T : Any> getClassForLogging(javaClass: Class<T>): Class<*> {
    return javaClass.enclosingClass?.takeIf {
        it.kotlin.companionObject?.java == javaClass
    } ?: javaClass
}

/**
 * Returns the logger for the current scope.
 */
inline fun <reified T : Any> T.logger(): Logger = LoggerFactory.getLogger(getClassForLogging(T::class.java))