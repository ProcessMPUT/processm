package processm.core.helpers

import kotlin.test.Test
import kotlin.test.assertEquals

class CounterTest {

    @Test
    fun test() {
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
        ctr.dec("a")
        assertEquals(1, ctr["a"])
        assertEquals(1, ctr["b"])
        ctr.dec("a")
        assertEquals(0, ctr["a"])
        assertEquals(1, ctr["b"])
        ctr.dec("a")
        assertEquals(0, ctr["a"])
        assertEquals(1, ctr["b"])
        ctr.inc(listOf("a", "b"))
        assertEquals(1, ctr["a"])
        assertEquals(2, ctr["b"])
        ctr.inc(listOf("a", "a"))
        assertEquals(3, ctr["a"])
        assertEquals(2, ctr["b"])
        ctr.inc("a", 4)
        assertEquals(7, ctr["a"])
        assertEquals(2, ctr["b"])
        ctr.inc(listOf("a", "b"), 4)
        assertEquals(11, ctr["a"])
        assertEquals(6, ctr["b"])
    }
}