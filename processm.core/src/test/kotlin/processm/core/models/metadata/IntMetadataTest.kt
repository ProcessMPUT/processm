package processm.core.models.metadata

import kotlin.math.pow
import kotlin.math.sqrt
import kotlin.test.Test
import kotlin.test.assertEquals

class IntMetadataTest {

    @Test
    fun test() {
        val mtd = IntMetadata()
        mtd.add(listOf(1, 2, 3, 14, 15))
        assertEquals((1 + 2 + 3 + 14 + 15) / 5.0, mtd.average)
        assertEquals(15, mtd.max)
        assertEquals(1, mtd.min)
        assertEquals(3.0, mtd.median)
        mtd.add(listOf())
        assertEquals((1 + 2 + 3 + 14 + 15) / 5.0, mtd.average)
        assertEquals(15, mtd.max)
        assertEquals(1, mtd.min)
        assertEquals(3.0, mtd.median)
        mtd.add(listOf(16))
        assertEquals((1 + 2 + 3 + 14 + 15 + 16) / 6.0, mtd.average)
        assertEquals(16, mtd.max)
        assertEquals(1, mtd.min)
        assertEquals((3 + 14) / 2.0, mtd.median)
        mtd.add(listOf(-1, 3))
        assertEquals((-1 + 1 + 2 + 3 + 3 + 14 + 15 + 16) / 8.0, mtd.average)
        assertEquals(16, mtd.max)
        assertEquals(-1, mtd.min)
        assertEquals(3.0, mtd.median)
        assertEquals(
            sqrt(
                (1 + 1 + 4 + 9 + 9 + 14 * 14 + 15 * 15 + 16 * 16) / 7.0 -
                        (-1 + 1 + 2 + 3 + 3 + 14 + 15 + 16).toDouble().pow(2) / (8.0 * 7.0)
            ), mtd.standardDeviation
        )
    }

    @Test
    fun medianEven() {
        val mtd = IntMetadata()
        mtd.add(listOf(2, 2, 1, 1))
        assertEquals(1.5, mtd.median)
    }

    @Test
    fun longerEvenMedianNonInteger() {
        val mtd = IntMetadata()
        mtd.add(listOf(1, 2, 2, 3, 3, 4, 4, 4, 4, 4))
        assertEquals(3.5, mtd.median)
    }

    @Test
    fun longerEvenMedianInteger() {
        val mtd = IntMetadata()
        mtd.add(listOf(1, 2, 2, 3, 3, 3, 4, 4, 4, 4))
        assertEquals(3.0, mtd.median)
    }
}