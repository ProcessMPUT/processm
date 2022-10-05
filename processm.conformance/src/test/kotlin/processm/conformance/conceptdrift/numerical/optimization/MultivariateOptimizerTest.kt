package processm.conformance.conceptdrift.numerical.optimization

import processm.core.log.Helpers.assertDoubleEquals
import kotlin.test.Test
import kotlin.test.assertEquals

abstract class MultivariateOptimizerTest {
    abstract fun instance(): MultivariateOptimizer

    @Test
    fun `5x^2+7x+11`() {
        val x = instance()(listOf(0.0)) { x -> listOf(10 * x[0] + 7) }
        assertDoubleEquals(-7.0 / 10, x.single())
    }

    @Test
    fun `(x-3)^2 div 2+(y-5)^2 div 5`() {
        val x = instance()(listOf(0.0, 0.0)) { x ->
            listOf(x[0] - 3, 2 * (x[1] - 5) / 5)
        }
        assertEquals(2, x.size)
        assertDoubleEquals(3.0, x[0])
        assertDoubleEquals(5.0, x[1])
    }
}