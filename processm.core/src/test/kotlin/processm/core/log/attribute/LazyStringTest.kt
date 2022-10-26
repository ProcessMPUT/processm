package processm.core.log.attribute

import kotlin.test.*

class LazyStringTest {
    @Test
    fun subsequence() {
        val text = "abcdefghijklmnopqrstuvwxyz"
        for (partLen in listOf(1, 2, 3, 4, 5)) {
            val parts =
                (text.indices step partLen).map { text.subSequence(it, (it + partLen).coerceAtMost(text.length)) }
            val lazy = LazyString(*parts.toTypedArray())
            for (start in 0 until text.length - 1)
                for (end in start + 1..text.length) {
                    assertEquals(text.subSequence(start, end), lazy.subSequence(start, end).toString())
                }
        }
    }

    @Test
    fun `hashCode is consistent between different split points`() {
        val text = "abcdefghijklmnopqrstuvwxyz"
        val hashes = ArrayList<Int>()
        for (partLen in listOf(1, 2, 3, 4, 5)) {
            val parts =
                (text.indices step partLen).map { text.subSequence(it, (it + partLen).coerceAtMost(text.length)) }
            val lazy = LazyString(*parts.toTypedArray())
            hashes.add(lazy.hashCode())
        }
        assertTrue { hashes.all { it == hashes[0] } }
    }
}