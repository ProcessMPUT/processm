package processm.experimental.helpers

import kotlin.test.*

class BitIntSetTest {

    @Test
    fun `add size contains`() {
        val bitIntSet = BitIntSet(1000)
        val values = listOf(5, 7, 32, 34, 37, 63, 64, 65, 127, 128, 256, 700)
        assertEquals(0, bitIntSet.size)
        for((i, v) in values.withIndex()) {
            bitIntSet.add(v)
            assertEquals(i+1, bitIntSet.size)
            for((j, y) in values.withIndex())
                assertEquals(j<=i, bitIntSet.contains(y))
        }
    }

    @Test
    fun `add iterate`() {
        val bitIntSet = BitIntSet(1000)
        val values = mutableListOf(5, 7, 32, 34, 37, 63, 64, 65, 127, 128, 256, 700)
        for(v in values)
            bitIntSet.add(v)
        val recreated = ArrayList(bitIntSet)
        recreated.sort()
        values.sort()
        assertEquals(values, recreated)
    }

    @Test
    fun `add reversed iterate`() {
        val bitIntSet = BitIntSet(1000)
        val values = mutableListOf(5, 7, 32, 34, 37, 63, 64, 65, 127, 128, 256, 700)
        for(v in values.reversed())
            bitIntSet.add(v)
        val recreated = ArrayList(bitIntSet)
        recreated.sort()
        values.sort()
        assertEquals(values, recreated)
    }

}