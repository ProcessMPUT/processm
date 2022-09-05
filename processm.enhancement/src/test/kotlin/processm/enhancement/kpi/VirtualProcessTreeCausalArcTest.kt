package processm.enhancement.kpi

import processm.core.helpers.mapToSet
import processm.core.models.processtree.ProcessTree
import processm.core.models.processtree.ProcessTreeActivity
import processm.core.models.processtree.SilentActivity
import kotlin.test.Test
import kotlin.test.assertEquals

class VirtualProcessTreeCausalArcTest {

    val ir = ProcessTreeActivity("invite reviewers")
    val gr1 = ProcessTreeActivity("get review 1")
    val gr2 = ProcessTreeActivity("get review 2")
    val gr3 = ProcessTreeActivity("get review 3")
    val grX = ProcessTreeActivity("get review X")
    val to1 = ProcessTreeActivity("time-out 1")
    val to2 = ProcessTreeActivity("time-out 2")
    val to3 = ProcessTreeActivity("time-out 3")
    val toX = ProcessTreeActivity("time-out X")
    val cr = ProcessTreeActivity("collect reviews")
    val de = ProcessTreeActivity("decide")
    val re = ProcessTreeActivity("reject")
    val ac = ProcessTreeActivity("accept")
    val iar = ProcessTreeActivity("invite additional reviewer")
    val tau = SilentActivity()

    @Test
    fun `perfectProcessTree including silent`() {
        val arcs = ProcessTree
            .parse("→(invite reviewers, ∧(×(get review 1, time-out 1), ×(get review 2, time-out 2), ×(get review 3, time-out 3)), collect reviews, decide, ⟲(τ, →(invite additional reviewer, ×(get review X, time-out X))), ×(accept, reject))")
            .generateArcs(includeSilent = true)
        val expected = setOf(
            VirtualProcessTreeMultiArc(setOf(ir), gr1),
            VirtualProcessTreeMultiArc(setOf(ir), gr2),
            VirtualProcessTreeMultiArc(setOf(ir), gr3),
            VirtualProcessTreeMultiArc(setOf(ir), to1),
            VirtualProcessTreeMultiArc(setOf(ir), to2),
            VirtualProcessTreeMultiArc(setOf(ir), to3),
            VirtualProcessTreeMultiArc(setOf(gr1, gr2, gr3), cr),
            VirtualProcessTreeMultiArc(setOf(gr1, gr2, to3), cr),
            VirtualProcessTreeMultiArc(setOf(gr1, to2, gr3), cr),
            VirtualProcessTreeMultiArc(setOf(to1, gr2, gr3), cr),
            VirtualProcessTreeMultiArc(setOf(to1, to2, gr3), cr),
            VirtualProcessTreeMultiArc(setOf(to1, gr2, to3), cr),
            VirtualProcessTreeMultiArc(setOf(gr1, to2, to3), cr),
            VirtualProcessTreeMultiArc(setOf(to1, to2, to3), cr),
            VirtualProcessTreeMultiArc(setOf(cr), de),
            VirtualProcessTreeMultiArc(setOf(de), tau),
            VirtualProcessTreeMultiArc(setOf(tau), ac),
            VirtualProcessTreeMultiArc(setOf(tau), re),
            VirtualProcessTreeMultiArc(setOf(tau), iar),
            VirtualProcessTreeMultiArc(setOf(iar), grX),
            VirtualProcessTreeMultiArc(setOf(iar), toX),
            VirtualProcessTreeMultiArc(setOf(grX), tau),
            VirtualProcessTreeMultiArc(setOf(toX), tau),
        )
        assertEquals(expected, arcs)
    }

    @Test
    fun `perfectProcessTree excluding silent`() {
        val arcs = ProcessTree
            .parse("→(invite reviewers, ∧(×(get review 1, time-out 1), ×(get review 2, time-out 2), ×(get review 3, time-out 3)), collect reviews, decide, ⟲(τ, →(invite additional reviewer, ×(get review X, time-out X))), ×(accept, reject))")
            .generateArcs(includeSilent = false)
        val expected = setOf(
            VirtualProcessTreeMultiArc(setOf(ir), gr1),
            VirtualProcessTreeMultiArc(setOf(ir), gr2),
            VirtualProcessTreeMultiArc(setOf(ir), gr3),
            VirtualProcessTreeMultiArc(setOf(ir), to1),
            VirtualProcessTreeMultiArc(setOf(ir), to2),
            VirtualProcessTreeMultiArc(setOf(ir), to3),
            VirtualProcessTreeMultiArc(setOf(gr1, gr2, gr3), cr),
            VirtualProcessTreeMultiArc(setOf(gr1, gr2, to3), cr),
            VirtualProcessTreeMultiArc(setOf(gr1, to2, gr3), cr),
            VirtualProcessTreeMultiArc(setOf(to1, gr2, gr3), cr),
            VirtualProcessTreeMultiArc(setOf(to1, to2, gr3), cr),
            VirtualProcessTreeMultiArc(setOf(to1, gr2, to3), cr),
            VirtualProcessTreeMultiArc(setOf(gr1, to2, to3), cr),
            VirtualProcessTreeMultiArc(setOf(to1, to2, to3), cr),
            VirtualProcessTreeMultiArc(setOf(cr), de),
            VirtualProcessTreeMultiArc(setOf(iar), grX),
            VirtualProcessTreeMultiArc(setOf(iar), toX)
        )
        assertEquals(expected, arcs)
    }

    @Test
    fun `loop with sequences`() {
        val arcs = ProcessTree
            .parse("→(a, ⟲(→(b,c), →(d, e), →(f, g)), h)")
            .generateArcs()
        val expected = listOf(
            "ab",
            "bc",
            "ch",
            "cd",
            "de",
            "eb",
            "cf",
            "fg",
            "gb"
        ).mapToSet { s ->
            VirtualProcessTreeMultiArc(
                setOf(ProcessTreeActivity(s[0].toString())),
                ProcessTreeActivity(s[1].toString())
            )
        }
        assertEquals(expected, arcs)
    }

    val a = ProcessTreeActivity("a")
    val b = ProcessTreeActivity("b")
    val c = ProcessTreeActivity("c")
    val d = ProcessTreeActivity("d")
    val e = ProcessTreeActivity("e")
    val f = ProcessTreeActivity("f")
    val g = ProcessTreeActivity("g")
    val h = ProcessTreeActivity("h")

    @Test
    fun `two parallels in sequence`() {
        val arcs = ProcessTree
            .parse("→(∧(a,b), ∧(c, d))")
            .generateArcs()
        assertEquals(
            arcs,
            setOf(
                VirtualProcessTreeMultiArc(setOf(a, b), c),
                VirtualProcessTreeMultiArc(setOf(a, b), d),
            )
        )
    }

    @Test
    fun `two parallels and exclusive in sequence`() {
        val arcs = ProcessTree
            .parse("→(∧(a,b), ∧(c, d), ×(e, f))")
            .generateArcs()
        assertEquals(
            setOf(
                VirtualProcessTreeMultiArc(setOf(a, b), c),
                VirtualProcessTreeMultiArc(setOf(a, b), d),
                VirtualProcessTreeMultiArc(setOf(c, d), e),
                VirtualProcessTreeMultiArc(setOf(c, d), f),
            ),
            arcs
        )
    }


    @Test
    fun `parallel, exclusive and parallel in sequence`() {
        val arcs = ProcessTree
            .parse("→(∧(a,b), ×(c, d), ∧(e, f))")
            .generateArcs()
        assertEquals(
            setOf(
                VirtualProcessTreeMultiArc(setOf(a, b), c),
                VirtualProcessTreeMultiArc(setOf(a, b), d),
                VirtualProcessTreeMultiArc(setOf(c), e),
                VirtualProcessTreeMultiArc(setOf(c), f),
                VirtualProcessTreeMultiArc(setOf(d), e),
                VirtualProcessTreeMultiArc(setOf(d), f),
            ),
            arcs
        )
    }

    @Test
    fun `two parallels with sequences in sequence`() {
        val arcs = ProcessTree
            .parse("→(∧(→(a,b), →(c,d)), ∧(→(e,f), →(g,h)))")
            .generateArcs()
        assertEquals(
            setOf(
                VirtualProcessTreeMultiArc(setOf(b, d), e),
                VirtualProcessTreeMultiArc(setOf(b, d), g),
                VirtualProcessTreeMultiArc(setOf(a), b),
                VirtualProcessTreeMultiArc(setOf(c), d),
                VirtualProcessTreeMultiArc(setOf(e), f),
                VirtualProcessTreeMultiArc(setOf(g), h),
            ),
            arcs
        )
    }

    @Test
    fun `parallel exclusives`() {
        val arcs = ProcessTree
            .parse("→(∧(×(a,b), ×(c,d)), e)")
            .generateArcs()
        assertEquals(
            setOf(
                VirtualProcessTreeMultiArc(setOf(a, c), e),
                VirtualProcessTreeMultiArc(setOf(a, d), e),
                VirtualProcessTreeMultiArc(setOf(b, c), e),
                VirtualProcessTreeMultiArc(setOf(b, d), e)
            ),
            arcs
        )
    }
}