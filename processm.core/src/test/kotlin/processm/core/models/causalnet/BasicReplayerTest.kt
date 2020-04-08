package processm.core.models.causalnet

import io.mockk.every
import io.mockk.mockk
import processm.core.log.Event
import processm.core.log.hierarchical.Trace
import kotlin.test.Test
import kotlin.test.assertEquals


class BasicReplayerTest {

    private fun event(name: String): Event {
        val e = mockk<Event>()
        every { e.conceptName } returns name
        every { e.lifecycleTransition } returns null
        return e
    }

    private fun trace(vararg nodes: Node): Trace =
        Trace(nodes.asList().map { event(it.name) }.asSequence())

    val a = Node("a")
    val b = Node("b")
    val c = Node("c")
    val d = Node("d")
    val e = Node("e")


    private fun Node.to(vararg targets: Node): Split = Split(targets.map { Dependency(this, it) }.toSet())

    private fun Node.from(vararg sources: Node): Join = Join(sources.map { Dependency(it, this) }.toSet())

    private fun Sequence<Sequence<BindingDecision>>.expecting(vararg what: List<Binding?>) {
        //this also compares order, possibly not the best idea?
        val actual = this.toList()
        val expected = what.toList()
        assertEquals(actual.size, what.size)
        (expected zip actual).forEach { (e, a) -> assertEquals(e, a.map { it.binding }.toList()) }
    }

    @Test
    fun `simple model`() {
        val model = causalnet {
            start = a
            end = d
            a splits b + c
            b splits d
            c splits d
            a joins b
            a joins c
            b + c join d
        }
        val r = BasicReplayer(model)
        r.replay(trace(a, b, c, d)).expecting(
            listOf(
                null, a.to(b, c),
                b.from(a), b.to(d),
                c.from(a), c.to(d),
                d.from(b, c), null
            )
        )
        r.replay(trace(a, c, b, d)).expecting(
            listOf(
                null, a.to(b, c),
                c.from(a), c.to(d),
                b.from(a), b.to(d),
                d.from(b, c), null
            )
        )
        r.replay(trace(a, d)).expecting()
    }

    @Test
    fun `trace with non-unique replay`() {
        val model = causalnet {
            start = a
            end = d
            a splits b + c
            b splits c + d or d
            c splits d
            a joins b
            a or a + b join c
            b + c join d
        }
        BasicReplayer(model).replay(trace(a, b, c, d)).expecting(
            listOf(null, a.to(b, c), b.from(a), b.to(d), c.from(a), c.to(d), d.from(b, c), null),
            listOf(null, a.to(b, c), b.from(a), b.to(c, d), c.from(a, b), c.to(d), d.from(b, c), null)
        )
    }
}