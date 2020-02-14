package processm.core.models.metadata

import org.junit.Test
import kotlin.test.assertEquals

class DoubleMetadataTest {

    @Test
    fun test() {
        val mtd = FlatDoubleMetadata()
        mtd.add(listOf(1.0, 2.0, 3.0, 14.0, 15.0))
        assertEquals((1 + 2 + 3 + 14 + 15) / 5.0, mtd.average)
        assertEquals(15.0, mtd.max)
        assertEquals(1.0, mtd.min)
        assertEquals(3.0, mtd.median)
        mtd.add(listOf())
        assertEquals((1 + 2 + 3 + 14 + 15) / 5.0, mtd.average)
        assertEquals(15.0, mtd.max)
        assertEquals(1.0, mtd.min)
        assertEquals(3.0, mtd.median)
        mtd.add(listOf(16.0))
        assertEquals((1 + 2 + 3 + 14 + 15 + 16) / 6.0, mtd.average)
        assertEquals(16.0, mtd.max)
        assertEquals(1.0, mtd.min)
        assertEquals((3.0 + 14) / 2, mtd.median)
        mtd.add(listOf(-1.0, 3.0))
        assertEquals((-1 + 1 + 2 + 3 + 3 + 14 + 15 + 16) / 8.0, mtd.average)
        assertEquals(16.0, mtd.max)
        assertEquals(-1.0, mtd.min)
        assertEquals(3.0, mtd.median)
    }
}