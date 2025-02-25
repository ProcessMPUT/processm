package processm.core.models.causalnet

import kotlin.test.*

class BindingTest {
    private val a = Node("register request")
    private val b = Node("examine thoroughly")
    private val c = Node("examine casually")

    @Test
    fun emptyJoin() {
        assertFailsWith(IllegalArgumentException::class) {
            Join(setOf())
        }
    }

    @Test
    fun emptySplit() {
        assertFailsWith(IllegalArgumentException::class) {
            Split(setOf())
        }
    }

    @Test
    fun normalJoin() {
        Join(setOf(Dependency(b, a), Dependency(c, a)))
    }

    @Test
    fun normalSplit() {
        Split(setOf(Dependency(a, b), Dependency(a, c)))
    }

    @Test
    fun joinWithDifferentTargets() {
        assertFailsWith(IllegalArgumentException::class) {
            Join(setOf(Dependency(a, b), Dependency(a, c)))
        }
    }

    @Test
    fun splitWithDifferentSources() {
        assertFailsWith(IllegalArgumentException::class) {
            Split(setOf(Dependency(b, a), Dependency(c, a)))
        }
    }

    @Test
    fun contains() {
        val s = Split(setOf(Dependency(a, b), Dependency(a, c)))
        assertTrue(Dependency(a, b) in s)
        assertFalse(Dependency(b, a) in s)
    }

    @Test
    fun size() {
        val s = Split(setOf(Dependency(a, b), Dependency(a, c)))
        assertEquals(2, s.size)
    }

    @Ignore("We decided that protecting against it is too expensive")
    @Test
    fun modifyJoinConstructorArgument() {
        val a = Node("a")
        val b = Node("b")
        val c = Node("c")
        val arg = HashSet(setOf(Dependency(a, b)))
        val j = Join(arg)
        assertEquals(setOf(Dependency(a, b)), j.dependencies)
        arg.add(Dependency(b, c))
        assertEquals(setOf(Dependency(a, b)), j.dependencies)
    }

    @Ignore("We decided that protecting against it is too expensive")
    @Test
    fun modifyJoinByCasting() {
        val a = Node("a")
        val b = Node("b")
        val c = Node("c")
        val j = Join(HashSet(setOf(Dependency(a, b))))
        assertEquals(setOf(Dependency(a, b)), j.dependencies)
        assertFails {
            (j.dependencies as MutableSet).add(Dependency(b, c))
        }
        assertEquals(setOf(Dependency(a, b)), j.dependencies)
    }

    @Ignore("We decided that protecting against it is too expensive")
    @Test
    fun modifySplitConstructorArgument() {
        val a = Node("a")
        val b = Node("b")
        val c = Node("c")
        val arg = HashSet(setOf(Dependency(a, b)))
        val j = Split(arg)
        assertEquals(setOf(Dependency(a, b)), j.dependencies)
        arg.add(Dependency(b, c))
        assertEquals(setOf(Dependency(a, b)), j.dependencies)
    }

    @Ignore("We decided that protecting against it is too expensive")
    @Test
    fun modifySplitByCasting() {
        val a = Node("a")
        val b = Node("b")
        val c = Node("c")
        val j = Split(HashSet(setOf(Dependency(a, b))))
        assertEquals(setOf(Dependency(a, b)), j.dependencies)
        assertFails {
            (j.dependencies as MutableSet).add(Dependency(b, c))
        }
        assertEquals(setOf(Dependency(a, b)), j.dependencies)
    }
}