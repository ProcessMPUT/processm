package processm.core.helpers

import kotlin.test.*

class LRUMapTest {

    @Test
    fun put() {
        val map = LRUMap<String, Int>()
        map["a"] = 1
        map["b"] = 2
        assertEquals(listOf("a" to 1, "b" to 2), map.asIterable().map { it.key to it.value })
        map["c"] = 3
        assertEquals(listOf("a" to 1, "b" to 2, "c" to 3), map.asIterable().map { it.key to it.value })
        map["b"] = 4
        assertEquals(listOf("a" to 1, "c" to 3, "b" to 4), map.asIterable().map { it.key to it.value })
    }

    @Ignore("This test is known to fail, as the implementation of LRUMap is incomplete")
    @Test
    fun compute() {
        val map = LRUMap<String, Int>()
        map.compute("a") { _, _ -> 1 }
        map.compute("b") { _, _ -> 2 }
        assertEquals(listOf("a" to 1, "b" to 2), map.asIterable().map { it.key to it.value })
        map.compute("c") { _, _ -> 3 }
        assertEquals(listOf("a" to 1, "b" to 2, "c" to 3), map.asIterable().map { it.key to it.value })
        map.compute("b") { _, _ -> 4 }
        assertEquals(listOf("a" to 1, "c" to 3, "b" to 4), map.asIterable().map { it.key to it.value })
    }

    @Ignore("This test is known to fail, as the implementation of LRUMap is incomplete")
    @Test
    fun computeIfPresent() {
        val map = LRUMap<String, Int>()
        map["a"] = 1
        map["b"] = 2
        assertEquals(listOf("a" to 1, "b" to 2), map.asIterable().map { it.key to it.value })
        map["c"] = 3
        assertEquals(listOf("a" to 1, "b" to 2, "c" to 3), map.asIterable().map { it.key to it.value })
        map.computeIfPresent("b") { _, _ -> 4 }
        assertEquals(listOf("a" to 1, "c" to 3, "b" to 4), map.asIterable().map { it.key to it.value })
    }

    @Test
    fun putAll() {
        val map = LRUMap<String, Int>()
        map.putAll(listOf("a" to 1))
        map.putAll(listOf("b" to 2))
        assertEquals(listOf("a" to 1, "b" to 2), map.asIterable().map { it.key to it.value })
        map.putAll(listOf("c" to 3))
        assertEquals(listOf("a" to 1, "b" to 2, "c" to 3), map.asIterable().map { it.key to it.value })
        map.putAll(listOf("b" to 4))
        assertEquals(listOf("a" to 1, "c" to 3, "b" to 4), map.asIterable().map { it.key to it.value })
    }
}