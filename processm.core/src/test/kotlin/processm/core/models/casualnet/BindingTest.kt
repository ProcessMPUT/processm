package processm.core.models.casualnet

import kotlin.test.Test
import processm.core.models.causalnet.*
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
}