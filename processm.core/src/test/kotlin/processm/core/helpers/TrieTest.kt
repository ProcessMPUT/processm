package processm.core.helpers

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TrieTest {

    @Test
    fun `build a trie`() {
        val trie = Trie<Int, Int> { 1 }
        assertEquals(1, trie.value)
        assertEquals(1, trie.getOrPut(1).value)
        assertEquals(1, trie.getOrPut(1).getOrPut(2).value)
        assertEquals(1, trie.getOrPut(1).getOrPut(2).getOrPut(3).value)
        trie.getOrPut(1).getOrPut(2).value = 12
        assertEquals(1, trie.value)
        assertEquals(1, trie.getOrPut(1).value)
        assertEquals(12, trie.getOrPut(1).getOrPut(2).value)
        assertEquals(1, trie.getOrPut(1).getOrPut(2).getOrPut(3).value)
        trie.getOrPut(1).getOrPut(2).getOrPut(3).value = 123
        assertEquals(1, trie.value)
        assertEquals(1, trie.getOrPut(1).value)
        assertEquals(12, trie.getOrPut(1).getOrPut(2).value)
        assertEquals(123, trie.getOrPut(1).getOrPut(2).getOrPut(3).value)
    }

    @Test
    fun `iterate a trie`() {
        val root = Trie<Int, Int> { 0 }
        root.getOrPut(1).getOrPut(2).getOrPut(3).value = 123
        root.getOrPut(1).getOrPut(3).getOrPut(2).value = 132
        root.getOrPut(1).getOrPut(2).getOrPut(4).value = 124
        val i = root.iterator()
        assertTrue { i.hasNext() }
        with(i.next()) {
            assertEquals(emptyList(), prefix)
            assertEquals(0, trie.value)
            assertEquals(setOf(1), trie.children.keys)
        }
        assertTrue { i.hasNext() }
        with(i.next()) {
            assertEquals(listOf(1), prefix)
            assertEquals(0, trie.value)
            assertEquals(setOf(2, 3), trie.children.keys)
        }
        assertTrue { i.hasNext() }
        with(i.next()) {
            assertEquals(listOf(1, 2), prefix)
            assertEquals(0, trie.value)
            assertEquals(setOf(3, 4), trie.children.keys)
        }
        assertTrue { i.hasNext() }
        with(i.next()) {
            assertEquals(listOf(1, 2, 3), prefix)
            assertEquals(123, trie.value)
            assertEquals(emptySet(), trie.children.keys)
        }
        assertTrue { i.hasNext() }
        with(i.next()) {
            assertEquals(listOf(1, 2, 4), prefix)
            assertEquals(124, trie.value)
            assertEquals(emptySet(), trie.children.keys)
        }
        assertTrue { i.hasNext() }
        with(i.next()) {
            assertEquals(listOf(1, 3), prefix)
            assertEquals(0, trie.value)
            assertEquals(setOf(2), trie.children.keys)
        }
        assertTrue { i.hasNext() }
        with(i.next()) {
            assertEquals(listOf(1, 3, 2), prefix)
            assertEquals(132, trie.value)
            assertEquals(emptySet(), trie.children.keys)
        }
        assertFalse { i.hasNext() }
    }
}