package processm.core.helpers

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TrieCounterTest {

    @Test
    fun `build a trie`() {
        val trie = TrieCounter<Int, Int> { 1 }
        assertEquals(1, trie.value)
        assertEquals(1, trie[1].value)
        assertEquals(1, trie[1][2].value)
        assertEquals(1, trie[1][2][3].value)
        trie[1][2].update { 12 }
        assertEquals(1, trie.value)
        assertEquals(1, trie[1].value)
        assertEquals(12, trie[1][2].value)
        assertEquals(1, trie[1][2][3].value)
        trie[1][2][3].update { 123 }
        assertEquals(1, trie.value)
        assertEquals(1, trie[1].value)
        assertEquals(12, trie[1][2].value)
        assertEquals(123, trie[1][2][3].value)
    }

    @Test
    fun `iterate a trie`() {
        val trie = TrieCounter<Int, Int> { 0 }
        trie[1][2][3].update { 123 }
        trie[1][3][2].update { 132 }
        trie[1][2][4].update { 124 }
        val i = trie.iterator()
        assertTrue { i.hasNext() }
        with(i.next()) {
            assertEquals(emptyList(), prefix)
            assertEquals(0, value.value)
            assertEquals(setOf(1), children)
        }
        assertTrue { i.hasNext() }
        with(i.next()) {
            assertEquals(listOf(1), prefix)
            assertEquals(0, value.value)
            assertEquals(setOf(2, 3), children)
        }
        assertTrue { i.hasNext() }
        with(i.next()) {
            assertEquals(listOf(1, 2), prefix)
            assertEquals(0, value.value)
            assertEquals(setOf(3, 4), children)
        }
        assertTrue { i.hasNext() }
        with(i.next()) {
            assertEquals(listOf(1, 2, 3), prefix)
            assertEquals(123, value.value)
            assertEquals(emptySet(), children)
        }
        assertTrue { i.hasNext() }
        with(i.next()) {
            assertEquals(listOf(1, 2, 4), prefix)
            assertEquals(124, value.value)
            assertEquals(emptySet(), children)
        }
        assertTrue { i.hasNext() }
        with(i.next()) {
            assertEquals(listOf(1, 3), prefix)
            assertEquals(0, value.value)
            assertEquals(setOf(2), children)
        }
        assertTrue { i.hasNext() }
        with(i.next()) {
            assertEquals(listOf(1, 3, 2), prefix)
            assertEquals(132, value.value)
            assertEquals(emptySet(), children)
        }
        assertFalse { i.hasNext() }
    }
}