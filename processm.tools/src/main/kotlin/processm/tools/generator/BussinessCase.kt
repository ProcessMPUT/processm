package processm.tools.generator

import kotlinx.coroutines.delay
import kotlin.reflect.KSuspendFunction0

data class BussinessCaseStep(val next: KSuspendFunction0<BussinessCaseStep?>, val delay: Long = 1L)

interface BussinessCase {

    suspend fun start(): BussinessCaseStep?

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