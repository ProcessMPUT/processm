package processm.helpers.map2d

import kotlin.test.*

class Map2DExtensionsTest {
    @Test
    fun `empty Map2D plus empty Map2D`() {
        val map1 = DoublingMap2D<Int, Int, Int>()
        val map2 = DoublingMap2D<Int, Int, Int>()
        val result = map1 + map2
        assertTrue(result.isEmpty())
        assertEquals(map1, result)
        assertEquals(map2, result)
    }

    @Test
    fun `empty Map2D plus non-empty Map2D`() {
        val map1 = DoublingMap2D<Int, Int, Int>()
        val map2 = DoublingMap2D<Int, Int, Int>()
        map2[1, 2] = 3
        val result = map1 + map2
        assertFalse(result.isEmpty())
        assertNotEquals(map1, result)
        assertEquals(map2, result)
    }

    @Test
    fun `non-empty Map2D plus empty Map2D`() {
        val map1 = DoublingMap2D<Int, Int, Int>()
        val map2 = DoublingMap2D<Int, Int, Int>()
        map1[1, 2] = 3
        val result = map1 + map2
        assertFalse(result.isEmpty())
        assertEquals(map1, result)
        assertNotEquals(map2, result)
    }

    @Test
    fun `union non-empty and different maps`() {
        val map1 = DoublingMap2D<Int, Int, Int>()
        val map2 = DoublingMap2D<Int, Int, Int>()
        map1[1, 2] = 3
        map2[4, 5] = 6
        val result = map1 + map2
        assertFalse(result.isEmpty())
        assertNotEquals(map1, result)
        assertNotEquals(map2, result)
        assertEquals(3, result[1, 2])
        assertEquals(6, result[4, 5])
    }

    @Test
    fun `union non-empty and equal maps`() {
        val map1 = DoublingMap2D<Int, Int, Int>()
        val map2 = DoublingMap2D<Int, Int, Int>()
        map1[1, 2] = 3
        map2[1, 2] = 3
        val result = map1 + map2
        assertFalse(result.isEmpty())
        assertEquals(map1, result)
        assertEquals(map2, result)
        assertEquals(3, result[1, 2])
    }

    @Test
    fun `union non-empty maps with the same keys but different content`() {
        val map1 = DoublingMap2D<Int, Int, Int>()
        val map2 = DoublingMap2D<Int, Int, Int>()
        map1[1, 2] = 3
        map2[1, 2] = 4
        val result = map1 + map2
        assertFalse(result.isEmpty())
        assertNotEquals(map1, result)
        assertEquals(map2, result)
        assertEquals(4, result[1, 2])
    }
}
