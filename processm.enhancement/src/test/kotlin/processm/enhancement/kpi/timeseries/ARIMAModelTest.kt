package processm.enhancement.kpi.timeseries

import org.junit.jupiter.api.assertThrows
import kotlin.test.Test
import kotlin.test.assertEquals

class ARIMAModelTest {

    @Test
    fun `simulate ▽³z(t)=10▽³z(t-1)+a(t) no noise`() {
        val model = ARIMAModel(listOf(10.0), emptyList(), 0.0, listOf(1.0, 13.0, 136.0), emptyList())
        val actual = model.simulate { step -> if (step == 0) 1000.0 else 0.0 }.drop(3).take(2).toList()
        assertEquals(1370.0, actual[0])
        assertEquals(13715.0, actual[1])
    }

    @Test
    fun `predict ▽³z(t)=10▽³z(t-1)+a(t) no noise`() {
        val model = ARIMAModel(listOf(10.0), emptyList(), 0.0, listOf(1.0, 13.0, 136.0), emptyList())
        assertEquals(1370.0, model.predict(listOf(1.0, 13.0, 136.0)))
        assertEquals(13715.0, model.predict(listOf(1.0, 13.0, 136.0, 1370.0)))
    }

    @Test
    fun `predict ▽³z(t)=2▽³z(t-1)+a(t)+5 no noise`() {
        val expected = listOf(0.0, 5.0, 30.0, 110.0, 320.0, 815.0, 1910.0, 4240.0)
        assertThrows<IllegalArgumentException> {
            val model = ARIMAModel(listOf(2.0), emptyList(), 5.0, expected.subList(0, 3), emptyList())
            for (i in 3 until expected.size)
                assertEquals(expected[i], model.predict(expected.subList(0, i)))
        }
    }

    @Test
    fun `predict ▽³z(t)=2▽³z(t-1)+a(t) a(t)=t+1`() {
        val expected = listOf(1.0, 7.0, 29.0, 93.0, 256.0, 638.0, 1486.0)
        val model = ARIMAModel(listOf(2.0), emptyList(), 0.0, expected.subList(0, 3), emptyList())
        for (t in 3 until expected.size)
            assertEquals(expected[t], model.predict(expected.subList(0, t)) + (t + 1))
    }

    @Test
    fun `predict ▽³z(t)=2▽³z(t-1)+a(t)-0_5*a(t-1) a(t)=t+1`() {
        val expected = listOf(1.0, 6.5, 25.5, 78.5, 209.5, 510.0, 1167.0)
        val model = ARIMAModel(listOf(2.0), listOf(0.5), 0.0, expected.subList(0, 3), emptyList())
        for (t in 3 until expected.size)
            assertEquals(expected[t], model.predict(expected.subList(0, t)) + (t + 1))
    }
}