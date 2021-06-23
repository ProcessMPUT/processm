package processm.experimental.performance

import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TrieTest {

    private lateinit var trie: Trie<Char>

    private fun String.asList(): List<Char> = this.map { it }

    @BeforeTest
    fun setup() {
        trie = Trie.build(
            listOf(
                "a".asList(),
                "ab".asList(),
                "acd".asList(),
                "ace".asList()
            )
        )
    }

    @Test
    fun containsAny1() {
        assertTrue {
            trie.containsAny(
                Trie.build(
                    listOf(
                        "a".asList(),
                        "ad".asList()
                    )
                )
            )
        }
    }

    @Test
    fun containsAny2() {
        assertFalse {
            trie.containsAny(
                Trie.build(
                    listOf(
                        "ad".asList(),
                        "ac".asList()
                    )
                )
            )
        }
    }

    @Test
    fun containsAny3() {
        assertTrue {
            trie.containsAny(
                Trie.build(
                    listOf(
                        "ad".asList(),
                        "acd".asList()
                    )
                )
            )
        }
    }

    @Test
    fun containsAnyNoCorooted1() {
        assertTrue {
            trie.children['c']!!.containsAny(
                Trie.build(
                    listOf(
                        "ad".asList(),
                        "acd".asList()
                    )
                )
            )
        }
    }

    @Test
    fun containsAnyNoCorooted2() {
        assertFalse {
            trie.children['c']!!.containsAny(
                Trie.build(
                    listOf(
                        "cd".asList()
                    )
                )
            )
        }
    }

    @Test
    fun contains() {
        assertTrue { "a".asList() in trie }
        assertFalse { "b".asList() in trie }
        assertFalse { "aa".asList() in trie }
        assertTrue { "ab".asList() in trie }
        assertFalse { "ac".asList() in trie }
        assertFalse { "acb".asList() in trie }
        assertTrue { "acd".asList() in trie }
        assertTrue { "ace".asList() in trie }
    }

    @Test
    fun test() {
        assertTrue { "acd".asList() in trie }
        assertTrue { "ace".asList() in trie }
        assertTrue(trie.remove("acd".asList()))
        assertFalse(trie.remove("acd".asList()))
        assertFalse { "acd".asList() in trie }
        assertTrue { "ace".asList() in trie }
        assertTrue(trie.remove("ace".asList()))
        assertFalse { "acd".asList() in trie }
        assertFalse { "ace".asList() in trie }
        trie.dump()
    }
}