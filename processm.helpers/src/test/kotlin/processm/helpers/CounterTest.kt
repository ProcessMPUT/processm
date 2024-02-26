package processm.helpers

import kotlin.test.Test
import kotlin.test.assertEquals

class CounterTest {

    @Test
    fun `inc increases only given field`() {
        val ctr = Counter<String>()
        assertEquals(0, ctr["a"])
        assertEquals(0, ctr["b"])
        ctr.inc("a")
        assertEquals(1, ctr["a"])
        assertEquals(0, ctr["b"])
        ctr.inc("a")
        assertEquals(2, ctr["a"])
        assertEquals(0, ctr["b"])
        ctr.inc("b")
        assertEquals(2, ctr["a"])
        assertEquals(1, ctr["b"])
    }

    @Test
    fun `inc with number for list without duplicates`() {
        val ctr = Counter<String>()
        ctr["a"] = 7
        ctr["b"] = 2
        ctr.inc(listOf("a", "b"), 4)
        assertEquals(11, ctr["a"])
        assertEquals(6, ctr["b"])
    }

    @Test
    fun `inc with number`() {
        val ctr = Counter<String>()
        ctr["a"] = 3
        ctr["b"] = 2
        ctr.inc("a", 4)
        assertEquals(7, ctr["a"])
        assertEquals(2, ctr["b"])
    }

    @Test
    fun `dec cannot decrease below 0`() {
        val ctr = Counter<String>()
        ctr["a"] = 2
        ctr["b"] = 1
        ctr.dec("a")
        assertEquals(1, ctr["a"])
        assertEquals(1, ctr["b"])
        ctr.dec("a")
        assertEquals(0, ctr["a"])
        assertEquals(1, ctr["b"])
        ctr.dec("a")
        assertEquals(0, ctr["a"])
        assertEquals(1, ctr["b"])
    }

    @Test
    fun `dec with number`() {
        val ctr = Counter<String>()
        ctr["a"] = 5
        ctr["b"] = 1
        ctr.dec("a", 3)
        assertEquals(2, ctr["a"])
        assertEquals(1, ctr["b"])
    }

    @Test
    fun `dec with number cannot decrease below 0`() {
        val ctr = Counter<String>()
        ctr["a"] = 5
        ctr["b"] = 1
        ctr.dec("a", 7)
        assertEquals(0, ctr["a"])
        assertEquals(1, ctr["b"])
    }

    @Test
    fun `list without duplicates`() {
        val ctr = Counter<String>()
        ctr["b"] = 1
        ctr.inc(listOf("a", "b"))
        assertEquals(1, ctr["a"])
        assertEquals(2, ctr["b"])
    }

    @Test
    fun `list with duplicates`() {
        val ctr = Counter<String>()
        ctr["a"] = 1
        ctr["b"] = 2
        ctr.inc(listOf("a", "a"))
        assertEquals(3, ctr["a"])
        assertEquals(2, ctr["b"])
    }
}
