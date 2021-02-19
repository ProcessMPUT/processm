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

    @Test
    fun `addAll size iterate`() {
        val bitIntSet = BitIntSet(1000)
        val values = mutableListOf(5, 7, 32, 34, 37, 63, 64, 65, 127, 128, 256, 700)
        assertEquals(0, bitIntSet.size)
        bitIntSet.addAll(values)
        assertEquals(values.size, bitIntSet.size)
        assertEquals(values, bitIntSet.toList())
    }

    @Test
    fun `addAll bitIntSet size iterate`() {
        val bitIntSet = BitIntSet(1000)
        val values = mutableListOf(5, 7, 32, 34, 37, 63, 64, 65, 127, 128, 256, 700)
        assertEquals(0, bitIntSet.size)
        bitIntSet.addAll(values)
        assertEquals(values.size, bitIntSet.size)
        val bitIntSet2 = BitIntSet(1000)
        bitIntSet2.addAll(bitIntSet)
        assertEquals(values.size, bitIntSet2.size)
        assertEquals(values, bitIntSet2.toList())
    }

    @Test
    fun `addAll bitIntSet to non empty size iterate`() {
        val values1 = listOf(5, 7, 32, 34, 37, 63, 64)
        val bitIntSet = BitIntSet(1000)
        bitIntSet.addAll(values1)
        val values2 = listOf(65, 127, 128, 256, 700)
        val bitIntSet2 = BitIntSet(1000)
        bitIntSet2.addAll(values2)
        bitIntSet.addAll(bitIntSet2)
        val expected=values1+values2
        assertEquals(expected.size, bitIntSet.size)
        assertEquals(expected, bitIntSet.toList())
    }

    @Test
    fun `addAll removeAll size`() {
        val bitIntSet = BitIntSet(1000)
        val values = setOf(5, 7, 32, 34, 37, 63, 64, 65, 127, 128, 256, 700)
        assertEquals(0, bitIntSet.size)
        bitIntSet.addAll(values)
        assertEquals(values.size, bitIntSet.size)
        val bitIntSet2 = BitIntSet(1000)
        val values2 = setOf(37, 63, 64, 65, 127, 128, 256, 257, 258, 259, 260)
        bitIntSet2.addAll(values2)
        bitIntSet.removeAll(bitIntSet2)
        val expected = values-values2
        assertEquals(expected.size, bitIntSet.size )
        assertEquals(expected, bitIntSet)
    }

    @Test
    fun `single element toList`() {
        val bitIntSet = BitIntSet(1000)
        bitIntSet.add(5)
        assertEquals(listOf(5), bitIntSet.toList())
    }

    @Test
    fun `single element on the border`() {
        val bitIntSet = BitIntSet(1000)
        bitIntSet.add(191)
        val i =bitIntSet.iterator()
        assertEquals(191, i.next())
        assertFalse(i.hasNext())
    }

}