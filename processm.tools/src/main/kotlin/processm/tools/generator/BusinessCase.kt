package processm.tools.generator

import kotlinx.coroutines.delay
import kotlin.reflect.KSuspendFunction0

/**
 * A step in the state machine represented by an implementation of [BusinessCase]
 *
 * @param next The next action to execute in the business case. The execution of the business case terminates once call to [next] returns null.
 * @param delay The delay to wait (in time units defined by the business case) before executing [next]
 */
data class BusinessCaseStep(val next: KSuspendFunction0<BusinessCaseStep?>, val delay: Long = 1L)

/**
 * Abstract business case, to be used by calling [invoke]. [invoke] then calls [start], which returns a [BusinessCaseStep].
 * [BusinessCaseStep.next] is then called, returning another [BusinessCaseStep], etc.
 * Once `null` is returned by [start] or [BusinessCaseStep.next], the execution terminates.
 */
interface BusinessCase {

    suspend fun start(): BusinessCaseStep?

    suspend operator fun invoke(timeUnit: Long) {
        require(timeUnit > 0)
        var nextStep = start()
        while (nextStep != null) {
            if (nextStep.delay > 0)
                delay(nextStep.delay * timeUnit)
            nextStep = nextStep.next()
        }
    }
}