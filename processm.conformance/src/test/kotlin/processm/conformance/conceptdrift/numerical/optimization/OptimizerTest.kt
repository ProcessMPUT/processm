package processm.conformance.conceptdrift.numerical.optimization

import processm.core.log.Helpers.assertDoubleEquals
import kotlin.test.Test

abstract class OptimizerTest {
    abstract fun instance(): Optimizer

    @Test
    fun `5x^2+7x+11`() {
        val x = instance()(0.0) { x -> 10 * x + 7 }
        assertDoubleEquals(-7.0 / 10, x)
    }
}