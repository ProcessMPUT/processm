package processm.core.verifiers.causalnet

import processm.core.models.causalnet.CausalNetStateImpl
import processm.core.models.causalnet.Dependency
import processm.core.models.causalnet.Node
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ActivityBindingTest {


    @Test
    fun `cartesian product`() {
        val exp = setOf("a" to 1, "a" to 2, "a" to 3, "b" to 1, "b" to 2, "b" to 3)
        val act = setOf("a", "b") times setOf(1, 2, 3)
        assertEquals(exp, act.toSet())
    }

    @Test
    fun `empty cartesian product`() {
        assertTrue { (setOf<Int>() times setOf(1)).isEmpty() }
        assertTrue { (setOf(2) times setOf<Int>()).isEmpty() }
    }

    @Test
    fun `state PM page 75`() {
        val a = Node("a")
        val b = Node("b")
        val d = Node("d")
        val e = Node("e")
        val s1 = ActivityBinding(a, setOf(), setOf(b, d), CausalNetStateImpl()).state
        assertEquals(setOf(Dependency(a, b), Dependency(a, d)), s1.uniqueSet().toSet())
        val s2 = ActivityBinding(d, setOf(a), setOf(e), s1).state
        assertEquals(setOf(Dependency(a, b), Dependency(d, e)), s2.uniqueSet().toSet())
        val s3 = ActivityBinding(b, setOf(a), setOf(e), s2).state
        assertEquals(setOf(Dependency(b, e), Dependency(d, e)), s3.uniqueSet().toSet())
        val s4 = ActivityBinding(e, setOf(b, d), setOf(), s3).state
        assertTrue { s4.isEmpty() }
    }
}
