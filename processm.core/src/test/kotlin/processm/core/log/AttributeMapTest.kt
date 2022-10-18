package processm.core.log

import kotlin.test.*
import kotlin.test.Test

class AttributeMapTest {

    private lateinit var map: AttributeMap<Int>

    private fun create(): AttributeMap<Int> {
        val map = AttributeMap<Int>()
        map["a"] = 1
        map[listOf("a", "b")] = 2
        map[listOf("a", "b", "c")] = 3
        map[listOf("a", "b", "c", "x")] = 23
        map[listOf("a", "b", "d")] = 4
        map[listOf("a", "e")] = 6
        map[listOf("a", "e", "f")] = 7
        map[listOf("a", "e", "g")] = 8
        map["h"] = 11
        map[listOf("h", "i")] = 12
        map[listOf("h", "i", "j")] = 13
        map[listOf("h", "i", "k")] = 14
        map[listOf("h", "l")] = 16
        map[listOf("h", "l", "m")] = 17
        map[listOf("h", "n")] = 18
        return map
    }

    @BeforeTest
    fun setup() {
        map = create()
    }

    @Test
    fun top() {
        assertEquals(2, map.size)
        assertFalse { map.isEmpty() }
        assertTrue { map.containsKey("a") }
        assertTrue { map.containsKey("h") }
        for (key in "bcdefgijklmnx")
            assertFalse { map.containsKey(key.toString()) }
        assertTrue { map.containsValue(1) }
        assertTrue { map.containsValue(11) }
        for (value in listOf(2, 3, 23, 4, 6, 7, 8, 12, 13, 14, 16, 17, 18))
            assertFalse { map.containsValue(value) }
        assertEquals(1, map["a"])
        assertEquals(11, map["h"])
    }

    @Test
    fun a() {
        val submap = map.children("a")
        assertEquals(2, submap.size)
        assertFalse { submap.isEmpty() }
        assertTrue { submap.containsKey("b") }
        assertTrue { submap.containsKey("e") }
        for (key in "ahcdfghijklmnx")
            assertFalse { submap.containsKey(key.toString()) }
        assertTrue { submap.containsValue(2) }
        assertTrue { submap.containsValue(6) }
        for (value in listOf(1, 3, 23, 4, 11, 7, 8, 12, 13, 14, 16, 17, 18))
            assertFalse { submap.containsValue(value) }
        assertEquals(2, submap["b"])
        assertEquals(6, submap["e"])
    }

    @Test
    fun ab1() {
        val submap = map.children("a").children("b")
        assertEquals(2, submap.size)
        assertEquals(setOf("c", "d"), submap.keys)
        assertFalse { submap.isEmpty() }
        assertTrue { submap.containsKey("c") }
        assertTrue { submap.containsKey("d") }
        for (key in "ahbefghijklmnx")
            assertFalse { submap.containsKey(key.toString()) }
        assertTrue { submap.containsValue(3) }
        assertTrue { submap.containsValue(4) }
        for (value in listOf(1, 2, 23, 6, 11, 7, 8, 12, 13, 14, 16, 17, 18))
            assertFalse { submap.containsValue(value) }
        assertEquals(3, submap["c"])
        assertEquals(4, submap["d"])
    }

    @Test
    fun ab2() {
        val submap = map.children(listOf("a", "b"))
        assertEquals(2, submap.size)
        assertEquals(setOf("c", "d"), submap.keys)
        assertFalse { submap.isEmpty() }
        assertTrue { submap.containsKey("c") }
        assertTrue { submap.containsKey("d") }
        for (key in "ahbefghijklmnx")
            assertFalse { submap.containsKey(key.toString()) }
        assertTrue { submap.containsValue(3) }
        assertTrue { submap.containsValue(4) }
        for (value in listOf(1, 2, 23, 6, 11, 7, 8, 12, 13, 14, 16, 17, 18))
            assertFalse { submap.containsValue(value) }
        assertEquals(3, submap["c"])
        assertEquals(4, submap["d"])
    }

    @Test
    fun abc() {
        val submap = map.children(listOf("a", "b", "c"))
        assertEquals(1, submap.size)
        assertEquals(setOf("x"), submap.keys)
        assertFalse { submap.isEmpty() }
        assertTrue { submap.containsKey("x") }
        for (key in "ahbefghijklmncd")
            assertFalse { submap.containsKey(key.toString()) }
        assertTrue { submap.containsValue(23) }
        for (value in listOf(1, 2, 3, 4, 6, 11, 7, 8, 12, 13, 14, 16, 17, 18))
            assertFalse { submap.containsValue(value) }
        assertEquals(23, submap["x"])
        assertNull(submap["d"])
    }

    @Test
    fun abcx() {
        val submap = map.children(listOf("a", "b", "c", "x"))
        assertTrue { submap.keys.isEmpty() }
        assertTrue { submap.entries.isEmpty() }
        assertEquals(0, submap.size)
        assertTrue { submap.isEmpty() }
        for (key in "ahbefghijklmncdx")
            assertFalse { submap.containsKey(key.toString()) }
        for (value in listOf(1, 2, 3, 4, 6, 11, 7, 8, 12, 13, 14, 16, 17, 18, 23))
            assertFalse { submap.containsValue(value) }
        assertNull(submap["x"])
        assertNull(submap["d"])
    }

    @Test
    fun `list get`() {
        assertEquals(3, map[listOf("a", "b", "c")])
        assertEquals(23, map[listOf("a", "b", "c", "x")])
        assertEquals(4, map[listOf("a", "b", "d")])
    }

    @Test
    fun `equals with attributemap`() {
        val other = create()
        assertEquals(map, other)
        assertEquals(other, map)
    }

    @Test
    fun `secondary constructor and equals with a map of other type`() {
        val base = mapOf("a" to 1, "b" to 2)
        val map = AttributeMap(base)
        assertEquals(base, map)
        assertEquals(map, base)
    }

    @Test
    fun `empty key`() {
        val map = AttributeMap<Int>()
        map[""] = 1
        map.children("")[""] = 2
        map["a"] = 3
        map[listOf("", "", "", "")] = 4
        assertEquals(2, map.size)
        assertEquals(setOf("", "a"), map.keys)
        assertEquals(1, map.children("").size)
        with(map.children("").entries.single()) {
            assertEquals("", key)
            assertEquals(2, value)
        }
        assertEquals(0, map.children("").children("").size)
        assertEquals(1, map.children("").children("").children("").size)
        with(map.children("").children("").children("").entries.single()) {
            assertEquals("", key)
            assertEquals(4, value)
        }
        assertEquals(0, map.children("").children("").children("").children("").size)
    }
}