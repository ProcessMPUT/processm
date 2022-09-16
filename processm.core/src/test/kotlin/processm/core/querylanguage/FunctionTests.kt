package processm.core.querylanguage

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class FunctionTests {

    @Test
    fun validScalarFunctionTest() {
        val function = Function("year", 0, 0, DateTimeLiteral("D2020-03-26", 0, 0))
        assertEquals(FunctionType.Scalar, function.functionType)
        assertEquals("year", function.name)
        assertEquals(1, function.children.size)
    }

    @Test
    fun validAggregateFunctionTest() {
        val function = Function("avg", 0, 0, Attribute("e:total", 0, 0))
        assertEquals(FunctionType.Aggregation, function.functionType)
        assertEquals("avg", function.name)
        assertEquals(1, function.children.size)
    }

    @Test
    fun invalidFunctionTest() {
        assertFailsWith<IllegalArgumentException> { Function("XYZ", 0, 0) }
        assertFailsWith<IllegalArgumentException> { Function("avg", 0, 0) }
        assertFailsWith<IllegalArgumentException> { Function("year", 0, 0) }
    }
}
