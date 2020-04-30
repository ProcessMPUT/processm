package processm.core.helpers.map2d

import kotlin.test.*

class DoublingMap2DTest {

    fun create() = DoublingMap2D<Int, Int, Int>()

    @Test
    fun `set and get`() {
        val m = create()
        m[1, 2] = 3
        assertEquals(3, m[1, 2])
        assertNotEquals(4, m[2, 1])
        m[2, 1] = 4
        assertEquals(3, m[1, 2])
        assertEquals(4, m[2, 1])
    }

    @Test
    fun replace() {
        val m = create()
        m[1, 2] = 3
        m[1, 2] = 4
        assertEquals(4, m[1, 2])
    }

    @Test
    fun `mutiple values in a row`() {
        val m = create()
        m[1, 2] = 3
        m[1, 3] = 4
        assertNull(m[0, 2])
        assertEquals(3, m[1, 2])
        assertEquals(4, m[1, 3])
    }

    @Test
    fun `read row`() {
        val m = create()
        m[1, 2] = 3
        m[1, 3] = 4
        assertTrue(m.getRow(0).toMap().isEmpty())
        assertEquals(mapOf(2 to 3, 3 to 4), m.getRow(1).toMap())
    }

    @Test
    fun `write row`() {
        val m = create()
        m[1, 2] = 3
        with(m.getRow(1)) {
            set(2, 4)
            set(3, 5)
        }
        assertEquals(mapOf(2 to 4, 3 to 5), m.getRow(1).toMap())
    }

    @Test
    fun `read column`() {
        val m = create()
        m[2, 1] = 3
        m[3, 1] = 4
        assertTrue(m.getColumn(0).toMap().isEmpty())
        assertEquals(mapOf(2 to 3, 3 to 4), m.getColumn(1).toMap())
    }

    @Test
    fun `write column`() {
        val m = create()
        m[2, 1] = 3
        with(m.getColumn(1)) {
            set(2, 4)
            set(3, 5)
        }
        assertEquals(mapOf(2 to 4, 3 to 5), m.getColumn(1).toMap())
    }

    @Test
    fun rows() {
        val m = create()
        m[2, 1] = 3
        assertEquals(setOf(2), m.rows)
        m[3, 2] = 3
        assertEquals(setOf(2, 3), m.rows)
    }

    @Test
    fun columns() {
        val m = create()
        m[2, 1] = 3
        assertEquals(setOf(1), m.columns)
        m[3, 2] = 3
        assertEquals(setOf(2, 1), m.columns)
    }
}
