package processm.core.models.casualnet

import kotlin.test.Test
import processm.core.models.causalnet.*
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertFailsWith

class BindingTest {
    private val a = ActivityInstance(Activity("register request"))
    private val b = ActivityInstance(Activity("examine thoroughly"))
    private val c = ActivityInstance(Activity("examine casually"))

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
    fun modifyJoinConstructorArgument() {
        val a = ActivityInstance(Activity("a"))
        val b = ActivityInstance(Activity("b"))
        val c = ActivityInstance(Activity("c"))
        val arg = HashSet(setOf(Dependency(a, b)))
        val j = Join(arg)
        assertEquals(setOf(Dependency(a, b)), j.dependencies)
        arg.add(Dependency(b, c))
        assertEquals(setOf(Dependency(a, b)), j.dependencies)
    }

    @Test
    fun modifyJoinByCasting() {
        val a = ActivityInstance(Activity("a"))
        val b = ActivityInstance(Activity("b"))
        val c = ActivityInstance(Activity("c"))
        val j = Join(HashSet(setOf(Dependency(a, b))))
        assertEquals(setOf(Dependency(a, b)), j.dependencies)
        assertFails {
            (j.dependencies as MutableSet).add(Dependency(b, c))
        }
        assertEquals(setOf(Dependency(a, b)), j.dependencies)
    }

    @Test
    fun modifySplitConstructorArgument() {
        val a = ActivityInstance(Activity("a"))
        val b = ActivityInstance(Activity("b"))
        val c = ActivityInstance(Activity("c"))
        val arg = HashSet(setOf(Dependency(a, b)))
        val j = Split(arg)
        assertEquals(setOf(Dependency(a, b)), j.dependencies)
        arg.add(Dependency(b, c))
        assertEquals(setOf(Dependency(a, b)), j.dependencies)
    }

    @Test
    fun modifySplitByCasting() {
        val a = ActivityInstance(Activity("a"))
        val b = ActivityInstance(Activity("b"))
        val c = ActivityInstance(Activity("c"))
        val j = Split(HashSet(setOf(Dependency(a, b))))
        assertEquals(setOf(Dependency(a, b)), j.dependencies)
        assertFails {
            (j.dependencies as MutableSet).add(Dependency(b, c))
        }
        assertEquals(setOf(Dependency(a, b)), j.dependencies)
    }
}