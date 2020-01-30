package processm.core.logging

import org.slf4j.Logger
import org.slf4j.LoggerFactory.getLogger
import kotlin.reflect.full.companionObject

fun <T : Any> getClassForLogging(javaClass: Class<T>): Class<*> {
    return javaClass.enclosingClass?.takeIf {
        it.kotlin.companionObject?.java == javaClass
    } ?: javaClass
}

inline fun <reified T : Any> T.logger(): Logger = getLogger(getClassForLogging(T::class.java))

fun Logger.enter() {
    if (!isTraceEnabled)
        return

    val stack = Thread.currentThread().stackTrace
    assert(stack.size > 2) // 0 refers to "getStackTrace", 1 to "enter", and 2 to the calling method
    assert(stack[0].methodName == Thread::getStackTrace.name)
    assert(stack[1].methodName == ::enter.name)
    this.trace("ENTERING ${stack[2].methodName}")
}

fun Logger.exit() {
    if (!isTraceEnabled)
        return

    val stack = Thread.currentThread().stackTrace
    assert(stack.size > 2) // 0 refers to "getStackTrace", 1 to "exit", and 2 to the calling method
    assert(stack[0].methodName == Thread::getStackTrace.name)
    assert(stack[1].methodName == ::exit.name)
    this.trace("EXITING  ${stack[2].methodName}")
}