package processm.core.models.processtree.execution

import processm.core.models.processtree.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ExecutionTest {


    private fun ExecutionNode.expecting(vararg what: ProcessTreeActivity): List<ActivityExecution> {
        val result = this.available.toList()
        assertEquals(what.toList(), result.map { it.base })
        assertEquals(what.isEmpty(), this.isComplete)
        return result
    }

    @Test
    fun `∧(a,×(b,c),⟲(d,e,f))`() {
        val a = ProcessTreeActivity("a")
        val b = ProcessTreeActivity("b")
        val c = ProcessTreeActivity("c")
        val d = ProcessTreeActivity("d")
        val e = ProcessTreeActivity("e")
        val f = ProcessTreeActivity("f")
        val loop = RedoLoop(d, e, f)
        val tree = processTree {
            Parallel(
                a,
                Exclusive(b, c),
                loop
            )
        }
        with(tree.root!!.executionNode(null)) {
            expecting(a, b, c, d)[3].execute()
            expecting(a, b, c, EndLoopSilentActivity(loop), e, f)[2].execute()
            expecting(a, EndLoopSilentActivity(loop), e, f)[2].execute()
            expecting(a, d)[0].execute()
            expecting(d)[0].execute()
            expecting(EndLoopSilentActivity(loop), e, f)[0].execute()
            expecting()
        }
    }

    @Test
    fun `⟲(⟲(a1,a2,a3),⟲(b1,b2,b3),⟲(c1,c2,c3))`() {
        val a1 = ProcessTreeActivity("a1")
        val a2 = ProcessTreeActivity("a2")
        val a3 = ProcessTreeActivity("a3")
        val b1 = ProcessTreeActivity("b1")
        val b2 = ProcessTreeActivity("b2")
        val b3 = ProcessTreeActivity("b3")
        val c1 = ProcessTreeActivity("c1")
        val c2 = ProcessTreeActivity("c2")
        val c3 = ProcessTreeActivity("c3")
        val a = RedoLoop(a1, a2, a3)
        val b = RedoLoop(b1, b2, b3)
        val c = RedoLoop(c1, c2, c3)
        val top = RedoLoop(a, b, c)
        val model = processTree { top }
        with(top.executionNode(null)) {
            expecting(a1)[0].execute()
            expecting(EndLoopSilentActivity(a), a2, a3)[1].execute()
            expecting(a1)[0].execute()
            expecting(EndLoopSilentActivity(a), a2, a3)[2].execute()
            expecting(a1)[0].execute()
            expecting(EndLoopSilentActivity(a), a2, a3)[0].execute()
            expecting(EndLoopSilentActivity(top), b1, c1)[0].execute()
            expecting()
        }
    }


    @Test
    fun `⟲(→(a1,a2,a3),→(b1,b2,b3),→(c1,c2,c3))`() {
        val a1 = ProcessTreeActivity("a1")
        val a2 = ProcessTreeActivity("a2")
        val a3 = ProcessTreeActivity("a3")
        val b1 = ProcessTreeActivity("b1")
        val b2 = ProcessTreeActivity("b2")
        val b3 = ProcessTreeActivity("b3")
        val c1 = ProcessTreeActivity("c1")
        val c2 = ProcessTreeActivity("c2")
        val c3 = ProcessTreeActivity("c3")
        val a = Sequence(a1, a2, a3)
        val b = Sequence(b1, b2, b3)
        val c = Sequence(c1, c2, c3)
        val top = RedoLoop(a, b, c)
        val model = processTree { top }
        val root = top.executionNode(null)
        with(root) {
            expecting(a1)[0].execute()
            expecting(a2)[0].execute()
            expecting(a3)[0].execute()
            expecting(EndLoopSilentActivity(top), b1, c1)[1].execute()
            expecting(b2)[0].execute()
            expecting(b3)[0].execute()
            expecting(a1)[0].execute()
        }
    }

    @Test
    fun `→(a,∧(→(b1,∧(c1,d1),e1),→(b2,∧(c2,d2),e2)),f)`() {
        val a = ProcessTreeActivity("a")
        val b1 = ProcessTreeActivity("b1")
        val b2 = ProcessTreeActivity("b2")
        val c1 = ProcessTreeActivity("c1")
        val c2 = ProcessTreeActivity("c2")
        val d1 = ProcessTreeActivity("d1")
        val d2 = ProcessTreeActivity("d2")
        val e1 = ProcessTreeActivity("e1")
        val e2 = ProcessTreeActivity("e2")
        val f = ProcessTreeActivity("f")
        val model = processTree {
            Sequence(
                a,
                Parallel(
                    Sequence(b1, Parallel(c1, d1), e1),
                    Sequence(b2, Parallel(c2, d2), e2)
                ),
                f
            )
        }
        val e =
            with(model.root!!.executionNode(null)) {
                expecting(a)[0].execute()
                expecting(b1, b2)[0].execute()
                expecting(c1, d1, b2)[1].execute()
                expecting(c1, b2)[1].execute()
                expecting(c1, c2, d2)[0].execute()
                expecting(e1, c2, d2)[1].execute()
                expecting(e1, d2)[0].execute()
                expecting(d2)[0].execute()
                expecting(e2)[0].execute()
                expecting(f)[0].execute()
                expecting()
            }
    }

    @Test
    fun `×(⟲(a,b),⟲(c,d))`() {
        val a = ProcessTreeActivity("a")
        val b = ProcessTreeActivity("b")
        val c = ProcessTreeActivity("c")
        val d = ProcessTreeActivity("d")
        val l1 = RedoLoop(a, b)
        val l2 = RedoLoop(c, d)
        val top = Exclusive(l1, l2)
        with(top.executionNode(null)) {
            expecting(a, c)[0].execute()
            expecting(EndLoopSilentActivity(l1), b)[1].execute()
            expecting(a)[0].execute()
            expecting(EndLoopSilentActivity(l1), b)[0].execute()
            expecting()
        }
        with(top.executionNode(null)) {
            expecting(a, c)[1].execute()
            expecting(EndLoopSilentActivity(l2), d)[1].execute()
            expecting(c)[0].execute()
            expecting(EndLoopSilentActivity(l2), d)[0].execute()
            expecting()
        }
    }

    @Test
    fun `children of an Activity are not supported`() {
        val a = ProcessTreeActivity("a")
        val b = ProcessTreeActivity("b")
        assertFailsWith<UnsupportedOperationException> { b.executionNode(a.executionNode(null)).execute() }
    }
}
