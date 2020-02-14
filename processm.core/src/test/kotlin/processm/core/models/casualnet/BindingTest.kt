package processm.core.models.casualnet

import org.junit.Test
import processm.core.models.causalnet.*

class BindingTest {
    private val a = ActivityInstance(Activity("register request"))
    private val b = ActivityInstance(Activity("examine thoroughly"))
    private val c = ActivityInstance(Activity("examine casually"))

    @Test(expected = IllegalArgumentException::class)
    fun emptyJoin() {
        Join(setOf())
    }

    @Test(expected = IllegalArgumentException::class)
    fun emptySplit() {
        Split(setOf())
    }

    @Test
    fun normalJoin() {
        Join(setOf(Dependency(b, a), Dependency(c, a)))
    }

    @Test
    fun normalSplit() {
        Split(setOf(Dependency(a, b), Dependency(a, c)))
    }

    @Test(expected = IllegalArgumentException::class)
    fun joinWithDifferentTargets() {
        Join(setOf(Dependency(a, b), Dependency(a, c)))
    }

    @Test(expected = IllegalArgumentException::class)
    fun splitWithDifferentSources() {
        Split(setOf(Dependency(b, a), Dependency(c, a)))
    }
}