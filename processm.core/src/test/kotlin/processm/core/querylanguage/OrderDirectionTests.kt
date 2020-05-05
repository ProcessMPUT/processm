package processm.core.querylanguage

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class OrderDirectionTests {
    @Test
    fun parseTest() {
        val directions = arrayOf(OrderDirection.Ascending, OrderDirection.Descending)
        for (direction in directions) {
            assertEquals(direction, OrderDirection.parse(direction.toString()))
            assertEquals(direction, OrderDirection.parse(direction.name))
        }
    }

    @Test
    fun invalidParseTest() {
        assertFailsWith<IllegalArgumentException> { OrderDirection.parse("XYZ") }
    }
}