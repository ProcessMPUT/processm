package processm.logging

import org.slf4j.Logger
import org.slf4j.LoggerFactory.getLogger
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
inline fun <reified T : Any> T.logger(): Logger = getLogger(getClassForLogging(T::class.java))

/**
 * Logs trace message before and after execution of the given logic.
 */
inline fun <reified T : Any, TResult> T.loggedScope(logExceptions: Boolean = false, scopeLogic: T.(logger: Logger) -> TResult): TResult {
    val logger = logger()

    try {
        logger.enter()
        return scopeLogic(logger)
    }
    catch (e :Exception) {
        if (logExceptions) logger.error("${e.message}", e)
        throw e
    }
    finally {
        logger.exit()
    }
}

/**
 * Logs on TRACE level the entrance to a function.
 */
@Suppress("DuplicatedCode") // the code is duplicated for the purpose of simplified stack reading
fun Logger.enter() {
    if (!isTraceEnabled)
        return

    val stack = Thread.currentThread().stackTrace
    assert(stack.size > 2) // 0 refers to "getStackTrace", 1 to "enter", and 2 to the calling method
    assert(stack[0].methodName == Thread::getStackTrace.name)
    assert(stack[1].methodName == ::enter.name)
    if (stack[2].methodName == "invokeSuspend")
        this.trace("ENTERING ${stack[2].className.substringAfterLast('.')}.${stack[2].methodName}")
    else
        this.trace("ENTERING ${stack[2].methodName}")
}

/**
 * Logs on TRACE level the exit from a function.
 */
@Suppress("DuplicatedCode") // the code is duplicated for the purpose of simplified stack reading
fun Logger.exit() {
    if (!isTraceEnabled)
        return

    val stack = Thread.currentThread().stackTrace
    assert(stack.size > 2) // 0 refers to "getStackTrace", 1 to "exit", and 2 to the calling method
    assert(stack[0].methodName == Thread::getStackTrace.name)
    assert(stack[1].methodName == ::exit.name)
    if (stack[2].methodName == "invokeSuspend")
        this.trace("EXITING  ${stack[2].className.substringAfterLast('.')}.${stack[2].methodName}")
    else
        this.trace("EXITING  ${stack[2].methodName}")
}

/**
 * Calls [lazy] to produce the message only if debugging is enabled.
 */
fun Logger.debug(lazy: () -> String?) {
    if (isDebugEnabled)
        debug(lazy() ?: return)
}

/**
 * Calls [lazy] to produce the message only if tracing is enabled.
 */
fun Logger.trace(lazy: () -> String?) {
    if (isTraceEnabled)
        trace(lazy() ?: return)
}
