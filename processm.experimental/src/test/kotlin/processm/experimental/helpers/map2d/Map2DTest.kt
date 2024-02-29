package processm.experimental.helpers.map2d

import com.google.common.collect.HashBasedTable
import com.google.common.collect.TreeBasedTable
import processm.helpers.map2d.Map2D
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull

interface Map2DTest<T : Map2D<Int, Int, Int>> {

    fun create(n: Int = 10): T

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
        val r = m.getRow(1)
        assertEquals(3, r[2])
        assertEquals(4, r[3])
    }

    @Test
    fun `write row`() {
        val m = create()
        m[1, 2] = 3
        with(m.getRow(1)) {
            set(2, 4)
            set(3, 5)
        }
        val r = m.getRow(1)
        assertEquals(4, r[2])
        assertEquals(5, r[3])
    }

    @Test
    fun `read column`() {
        val m = create()
        m[2, 1] = 3
        m[3, 1] = 4
        val c = m.getColumn(1)
        assertEquals(3, c[2])
        assertEquals(4, c[3])
        assertNull(c[4])
    }

    @Test
    fun `write column`() {
        val m = create()
        m[2, 1] = 3
        with(m.getColumn(1)) {
            set(2, 4)
            set(3, 5)
        }
        val c = m.getColumn(1)
        assertEquals(4, c[2])
        assertEquals(5, c[3])
        assertNull(c[4])
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

class WrappingMap2DTest : Map2DTest<WrappingMap2D<Int, Int, Int>> {
    override fun create(n: Int): WrappingMap2D<Int, Int, Int> = WrappingMap2D()
}

class DoublingMap2DWithOneUpdateTest : Map2DTest<DoublingMap2DWithOneUpdate<Int, Int, Int>> {
    override fun create(n: Int): DoublingMap2DWithOneUpdate<Int, Int, Int> = DoublingMap2DWithOneUpdate()
}

class DenseMap2DTest : Map2DTest<DenseMap2D<Int, Int, Int>> {
    override fun create(n: Int) = DenseMap2D<Int, Int, Int>(n, n)
}

class GuavaHashBasedTableMap2DTest : Map2DTest<GuavaWrappingMap2D<Int, Int, Int>> {
    override fun create(n: Int) =
        GuavaWrappingMap2D(HashBasedTable.create<Int, Int, Int>())
}

class GuavaTreeBasedTableMap2DTest : Map2DTest<GuavaWrappingMap2D<Int, Int, Int>> {
    override fun create(n: Int) =
        GuavaWrappingMap2D(TreeBasedTable.create<Int, Int, Int>())
}

class WheelReinventingMap2DTest : Map2DTest<WheelReinventingMap2D<Int, Int, Int>> {
    override fun create(n: Int) = WheelReinventingMap2D<Int, Int, Int>(n * n / 3)
}
