package processm.core.models.causalnet

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class NodeTest {

    @Test
    fun sameActivityNoId() {
        val a1 = Node("a")
        val a2 = Node("a")
        assertTrue { a1 == a2 }
        assertTrue { a1.hashCode() == a2.hashCode() }
    }

    @Test
    fun differentActivityNoId() {
        val a1 = Node("a")
        val a2 = Node("b")
        assertFalse { a1 == a2 }
        assertFalse { a1.hashCode() == a2.hashCode() }
    }

    @Test
    fun sameActivitySameId() {
        val a1 = Node("a", "1")
        val a2 = Node("a", "1")
        assertTrue { a1 == a2 }
        assertTrue { a1.hashCode() == a2.hashCode() }
    }

    @Test
    fun sameActivityDifferentId() {
        val a1 = Node("a", "1")
        val a2 = Node("a", "2")
        assertFalse { a1 == a2 }
        assertFalse { a1.hashCode() == a2.hashCode() }
    }

    @Test
    fun differentActivitySameId() {
        val a1 = Node("a", "1")
        val a2 = Node("b", "1")
        assertFalse { a1 == a2 }
        assertFalse { a1.hashCode() == a2.hashCode() }
    }

    @Test
    fun differentActivityDifferentId() {
        val a1 = Node("a", "1")
        val a2 = Node("b", "2")
        assertFalse { a1 == a2 }
        assertFalse { a1.hashCode() == a2.hashCode() }
    }
}