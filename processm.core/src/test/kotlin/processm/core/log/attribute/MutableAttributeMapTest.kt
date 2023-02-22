package processm.core.log.attribute

import processm.core.log.attribute.AttributeMap.Companion.SEPARATOR
import processm.core.log.attribute.AttributeMap.Companion.SEPARATOR_CHAR
import processm.core.log.attribute.AttributeMap.Companion.BEFORE_STRING
import kotlin.test.*

class MutableAttributeMapTest {

    private lateinit var map: MutableAttributeMap

    private operator fun MutableAttributeMap.set(path: List<String>, value: Long) {
        var map = this
        for (key in path.subList(0, path.size - 1))
            map = map.children(key)
        map[path.last()] = value
    }

    private fun MutableAttributeMap.children(path: List<String>): MutableAttributeMap {
        var map = this
        for (key in path)
            map = map.children(key)
        return map
    }

    private fun create(): MutableAttributeMap {
        val map = MutableAttributeMap()
        map["a"] = 1L
        map[listOf("a", "b")] = 2L
        map[listOf("a", "b", "c")] = 3L
        map[listOf("a", "b", "c", "x")] = 23L
        map[listOf("a", "b", "d")] = 4L
        map[listOf("a", "e")] = 6L
        map[listOf("a", "e", "f")] = 7L
        map[listOf("a", "e", "g")] = 8L
        map["h"] = 11L
        map[listOf("h", "i", "j")] = 13L
        map[listOf("h", "i", "k")] = 14L
        map[listOf("h", "l")] = 16L
        map[listOf("h", "l", "m")] = 17L
        map[listOf("h", "n")] = 18L
        return map
    }

    @BeforeTest
    fun setup() {
        map = create()
    }

    @Test
    fun childrenKeys() {
        assertEquals(setOf("a", "h"), map.childrenKeys)
        assertEquals(setOf("i", "l"), map.children("h").childrenKeys)
    }

    @Test
    fun top() {
        assertEquals(2, map.size)
        assertFalse { map.isEmpty() }
        assertTrue { map.containsKey("a") }
        assertTrue { map.containsKey("h") }
        for (key in "bcdefgijklmnx")
            assertFalse { map.containsKey(key.toString()) }
        assertTrue { map.containsValue(1L) }
        assertTrue { map.containsValue(11L) }
        for (value in listOf(2L, 3L, 23L, 4L, 6L, 7L, 8L, 12L, 13L, 14L, 16L, 17L, 18L))
            assertFalse { map.containsValue(value) }
        assertEquals(1L, map["a"])
        assertEquals(11L, map["h"])
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
        assertTrue { submap.containsValue(2L) }
        assertTrue { submap.containsValue(6L) }
        for (value in listOf(1L, 3L, 23L, 4L, 11L, 7L, 8L, 12L, 13L, 14L, 16L, 17L, 18L))
            assertFalse { submap.containsValue(value) }
        assertEquals(2L, submap["b"])
        assertEquals(6L, submap["e"])
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
        assertTrue { submap.containsValue(3L) }
        assertTrue { submap.containsValue(4L) }
        for (value in listOf(1L, 2L, 23L, 6L, 11L, 7L, 8L, 12L, 13L, 14L, 16L, 17L, 18L))
            assertFalse { submap.containsValue(value) }
        assertEquals(3L, submap["c"])
        assertEquals(4L, submap["d"])
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
        assertTrue { submap.containsValue(3L) }
        assertTrue { submap.containsValue(4L) }
        for (value in listOf(1L, 2L, 23L, 6L, 11L, 7L, 8L, 12L, 13L, 14L, 16L, 17L, 18L))
            assertFalse { submap.containsValue(value) }
        assertEquals(3L, submap["c"])
        assertEquals(4L, submap["d"])
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
        assertTrue { submap.containsValue(23L) }
        for (value in listOf(1L, 2L, 3L, 4L, 6L, 11L, 7L, 8L, 12L, 13L, 14L, 16L, 17L, 18L))
            assertFalse { submap.containsValue(value) }
        assertEquals(23L, submap["x"])
        assertFailsWith<NoSuchElementException> { submap["d"] }
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
        for (value in listOf(1L, 2L, 3L, 4L, 6L, 11L, 7L, 8L, 12L, 13L, 14L, 16L, 17L, 18L, 23L))
            assertFalse { submap.containsValue(value) }
        assertFailsWith<NoSuchElementException> { submap["x"] }
        assertFailsWith<NoSuchElementException> { submap["d"] }
    }

    @Test
    fun `equals with attributemap`() {
        val other = create()
        assertEquals(map, other)
        assertEquals(other, map)
    }

    @Test
    fun `empty key`() {
        val map = MutableAttributeMap()
        map[""] = 1L
        map.children("")[""] = 2L
        map["a"] = 3L
        map[listOf("", "", "", "")] = 4L
        assertEquals(2, map.size)
        assertEquals(setOf("", "a"), map.keys)
        assertEquals(setOf(""), map.childrenKeys)
        assertEquals(1, map.children("").size)
        with(map.children("").entries.single()) {
            assertEquals("", key)
            assertEquals(2L, value)
        }
        assertEquals(setOf(""), map.children("").childrenKeys)
        assertEquals(0, map.children("").children("").size)
        assertEquals(setOf(""), map.children("").children("").childrenKeys)
        assertEquals(1, map.children("").children("").children("").size)
        with(map.children("").children("").children("").entries.single()) {
            assertEquals("", key)
            assertEquals(4L, value)
        }
        assertEquals(0, map.children("").children("").children("").children("").size)
    }

    @Test
    fun `handling null`() {
        val map = MutableAttributeMap()
        map["a"] = 1L
        assertEquals(1L, map["a"])
        assertFailsWith<NoSuchElementException> { map["b"] }
        assertFailsWith<NoSuchElementException> { map["c"] }
        assertFalse { map.containsValue(null) }
        map["b"] = null
        assertEquals(1L, map["a"])
        assertNull(map["b"])
        assertFailsWith<NoSuchElementException> { map["c"] }
        assertTrue { map.containsValue(null) }
    }

    @Test
    fun `compute if absent`() {
        val map = MutableAttributeMap()
        assertFailsWith<NoSuchElementException> { map["a"] }
        assertFailsWith<NoSuchElementException> { map["b"] }
        assertFailsWith<NoSuchElementException> { map["c"] }
        map.computeIfAbsent("a") { 1L }
        assertEquals(1L, map["a"])
        assertFailsWith<NoSuchElementException> { map["b"] }
        assertFailsWith<NoSuchElementException> { map["c"] }
        map.computeIfAbsent("a") { null }
        assertEquals(1L, map["a"])
        assertFailsWith<NoSuchElementException> { map["b"] }
        assertFailsWith<NoSuchElementException> { map["c"] }
        map.computeIfAbsent("b") { null }
        assertEquals(1L, map["a"])
        assertNull(map["b"])
        assertFailsWith<NoSuchElementException> { map["c"] }
        map.computeIfAbsent("b") { 1L }
        assertEquals(1L, map["a"])
        assertNull(map["b"])
        assertFailsWith<NoSuchElementException> { map["c"] }
    }

    @Test
    fun deepCopy() {
        val other = MutableAttributeMap(map)
        assertEquals(map, other)
    }

    @Test
    fun `key starting with 07`() {
        val key1 = "\u0007bell"
        val key2 = "normal key"
        val map = MutableAttributeMap()
        map[key1] = "a"
        map[key2] = "b"
        assertEquals(setOf(key1, key2), map.keys)
    }

    @Test
    fun `child key starting with 07`() {
        val key1 = "\u0007bell"
        val key2 = "normal key"
        val map = MutableAttributeMap()
        map.children(key1)["a"] = "b"
        map.children(key2)["a"] = "b"
        assertEquals(setOf(key1, key2), map.childrenKeys)
    }

    @Test
    fun `key in a child starting with 07`() {
        val key1 = "\u0007bell"
        val key2 = "normal key"
        val map = MutableAttributeMap().children("a")
        map[key1] = "a"
        map[key2] = "b"
        assertEquals(setOf(key1, key2), map.keys)
    }

    @Test
    fun `child key in a child starting with 07`() {
        val key1 = "\u0007bell"
        val key2 = "normal key"
        val map = MutableAttributeMap().children("blah")
        map.children(key1)["a"] = "b"
        map.children(key2)["a"] = "b"
        assertEquals(setOf(key1, key2), map.childrenKeys)
    }

    @Test
    fun `prefixes at top`() {
        val BEFORE = SEPARATOR_CHAR - 1
        val AFTER = SEPARATOR_CHAR + 1
        val map = MutableAttributeMap()
        map["$BEFORE"] = 1
        map["$BEFORE$BEFORE"] = 2
        map["$BEFORE$AFTER"] = 5
        map["$AFTER"] = 6
        map["$AFTER$BEFORE"] = 7
        map["$AFTER$AFTER"] = 8
        assertEquals(
            setOf("$BEFORE", "$BEFORE$BEFORE", "$BEFORE$AFTER", "$AFTER", "$AFTER$BEFORE", "$AFTER$AFTER"),
            map.keys
        )
        assertEquals(0, map.childrenKeys.size)
        map.children("$BEFORE")["a"] = 1
        map.children("$BEFORE$BEFORE")["a"] = 1
        map.children("$BEFORE$AFTER")["a"] = 1
        map.children("$AFTER")["a"] = 1
        map.children("$AFTER$BEFORE")["a"] = 1
        map.children("$AFTER$AFTER")["a"] = 1
        assertEquals(
            setOf("$BEFORE", "$BEFORE$BEFORE", "$BEFORE$AFTER", "$AFTER", "$AFTER$BEFORE", "$AFTER$AFTER"),
            map.keys
        )
        assertEquals(
            setOf("$BEFORE", "$BEFORE$BEFORE", "$BEFORE$AFTER", "$AFTER", "$AFTER$BEFORE", "$AFTER$AFTER"),
            map.childrenKeys
        )
    }

    @Test
    fun `prefixes in children`() {
        val BEFORE = SEPARATOR_CHAR - 1
        val AFTER = SEPARATOR_CHAR + 1
        val top = MutableAttributeMap()
        val children = listOf(
            top.children("$BEFORE"),
            top.children("$BEFORE$BEFORE"),
            top.children("$BEFORE$AFTER"),
            top.children("$AFTER"),
            top.children("$AFTER$BEFORE"),
            top.children("$AFTER$AFTER")
        )
        for (map in children) {
            map["$BEFORE"] = 1
            map["$BEFORE$BEFORE"] = 2
            map["$BEFORE$AFTER"] = 5
            map["$AFTER"] = 6
            map["$AFTER$BEFORE"] = 7
            map["$AFTER$AFTER"] = 8
        }
        for (map in children) {
            assertEquals(
                setOf("$BEFORE", "$BEFORE$BEFORE", "$BEFORE$AFTER", "$AFTER", "$AFTER$BEFORE", "$AFTER$AFTER"),
                map.keys
            )
            assertEquals(0, map.childrenKeys.size)
        }
        for (map in children) {
            map.children("$BEFORE")["a"] = 1
            map.children("$BEFORE$BEFORE")["a"] = 1
            map.children("$BEFORE$AFTER")["a"] = 1
            map.children("$AFTER")["a"] = 1
            map.children("$AFTER$BEFORE")["a"] = 1
            map.children("$AFTER$AFTER")["a"] = 1
        }
        for (map in children) {
            assertEquals(
                setOf("$BEFORE", "$BEFORE$BEFORE", "$BEFORE$AFTER", "$AFTER", "$AFTER$BEFORE", "$AFTER$AFTER"),
                map.keys
            )
            assertEquals(
                setOf("$BEFORE", "$BEFORE$BEFORE", "$BEFORE$AFTER", "$AFTER", "$AFTER$BEFORE", "$AFTER$AFTER"),
                map.childrenKeys
            )
        }
    }
}