package processm.conformance.conceptdrift.numerical.integration

import processm.core.log.Helpers.assertDoubleEquals
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.test.Test

abstract class IntegratorTest {

    abstract fun instance(): Integrator

    @Test
    fun `5x+7`() {
        val integrator = instance()
        val f = { x: Double -> 5 * x + 7 }
        val F = { x: Double -> 5 * x.pow(2) / 2 + 7 * x }
        assertDoubleEquals(F(10.0) - F(0.0), integrator.invoke(0.0, 10.0, f))
    }

    @Test
    fun `5x^2+7x+11`() {
        val integrator = instance()
        val f = { x: Double -> 5 * x.pow(2) + 7 * x + 11 }
        val F = { x: Double -> 5 * x.pow(3) / 3 + 7 * x.pow(2) / 2 + 11 * x }
        assertDoubleEquals(F(10.0) - F(0.0), integrator.invoke(0.0, 10.0, f))
    }

    @Test
    fun `sin(x)+cos(x)`() {
        val integrator = instance()
        val f = { x: Double -> sin(x) + cos(x) }
        val F = { x: Double -> -cos(x) + sin(x) }
        assertDoubleEquals(F(10.0) - F(0.0), integrator.invoke(0.0, 10.0, f))
    }
}