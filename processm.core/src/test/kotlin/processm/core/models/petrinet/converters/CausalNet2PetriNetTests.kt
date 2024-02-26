package processm.core.models.petrinet.converters

import processm.core.models.causalnet.*
import processm.core.models.petrinet.PetriNetInstance
import processm.core.models.petrinet.Transition
import processm.core.models.petrinet.TransitionExecution
import processm.helpers.mapToSet
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CausalNet2PetriNetTests {

    fun PetriNetInstance.expecting(vararg transitions: Transition, final: Boolean? = null): List<TransitionExecution> {
        assertEquals(final ?: transitions.isEmpty(), this.isFinalState)
        val available = this.availableActivityExecutions.toList()
        assertEquals(transitions.size, available.size)
        for (transition in transitions)
            assertTrue(available.any { a -> transition == a.activity })

        return available
    }

    //activities inspired by Fig 3.12 in "Process Mining" by Van van der Alst
    private val a = Node("register request")
    private val b = Node("examine thoroughly")
    private val c = Node("examine casually")
    private val d = Node("check ticket")
    private val e = Node("decide")
    private val f = Node("reinitiate request")
    private val g = Node("pay compensation")
    private val h = Node("reject request")
    private val z = Node("end")

    //constructing model represented at Fig 3.12 in "Process Mining" by Wil van der Aalst
    fun constructCnet(): MutableCausalNet {
        val mm = MutableCausalNet(start = a, end = z)
        mm.addInstance(a, b, c, d, e, f, g, h, z)
        listOf(
            a to b, a to c, a to d, b to e, c to e, d to e, e to f, e to g,
            e to h, f to d, f to c, f to b, g to z, h to z
        ).forEach { mm.addDependency(it.first, it.second) }
        listOf(
            setOf(a to b, a to d),
            setOf(a to c, a to d),
            setOf(b to e),
            setOf(c to e),
            setOf(d to e),
            setOf(e to g),
            setOf(e to h),
            setOf(e to f),
            setOf(f to d, f to b),
            setOf(f to d, f to c),
            setOf(g to z),
            setOf(h to z)
        ).map { split -> split.mapToSet { Dependency(it.first, it.second) } }
            .forEach { mm.addSplit(Split(it)) }
        listOf(
            setOf(a to b),
            setOf(f to b),
            setOf(a to c),
            setOf(f to c),
            setOf(a to d),
            setOf(f to d),
            setOf(b to e, d to e),
            setOf(c to e, d to e),
            setOf(e to f),
            setOf(e to g),
            setOf(e to h),
            setOf(g to z),
            setOf(h to z)
        ).map { join -> join.mapToSet { Dependency(it.first, it.second) } }
            .forEach { mm.addJoin(Join(it)) }
        assertEquals(setOf(a, b, c, d, e, f, g, h, z), mm.instances)
        assertTrue(mm.outgoing[a]?.all { it.source == a } == true)
        assertEquals(setOf(b, c, d), mm.outgoing[a]?.mapToSet { it.target })
        assertFalse { z in mm.outgoing }
        assertTrue { mm.incoming[z]?.all { it.target == z } == true }
        assertEquals(setOf(g, h), mm.incoming[z]?.mapToSet { it.source })
        assertEquals(2, mm.splits[a]?.size)
        assertEquals(1, mm.splits[b]?.size)
        assertFalse { z in mm.splits }
        assertEquals(2, mm.joins[e]?.size)
        assertEquals(1, mm.joins[f]?.size)
        assertEquals(setOf(Dependency(e, f)), mm.joins[f]?.first()?.dependencies)
        assertFalse { a in mm.joins }
        return mm
    }

    @Test
    fun `PM book Fig 3 12 C-net to Petri net conversion`() {
        val cnet = constructCnet()
        val petri = cnet.toPetriNet()

        assertEquals(
            cnet.activities.filter { !it.isSilent }.mapToSet { it.name },
            petri.activities.filter { !it.isSilent }.mapToSet { it.name }
        )

        assertEquals(
            cnet.startActivities.mapToSet { it.name },
            petri.startActivities.mapToSet { it.name }
        )

        assertEquals(
            cnet.endActivities.mapToSet { it.name },
            petri.endActivities.mapToSet { it.name }
        )

        for (transition in petri.transitions.filter { !it.isSilent }) {
            assertEquals(1, transition.inPlaces.size)
            assertEquals(1, transition.outPlaces.size)
        }

        val Ta = petri.transitions.first { it.name == a.name }
        val TaSplits = petri.transitions.filter { it.inPlaces == Ta.outPlaces }
        val Tb = petri.transitions.first { it.name == b.name }
        val TbJoins = petri.transitions.filter { it.outPlaces == Tb.inPlaces }
        val Tc = petri.transitions.first { it.name == c.name }
        val TcJoins = petri.transitions.filter { it.outPlaces == Tc.inPlaces }
        val Td = petri.transitions.first { it.name == d.name }
        val TdJoins = petri.transitions.filter { it.outPlaces == Td.inPlaces }
        val Te = petri.transitions.first { it.name == e.name }
        val TeJoins = petri.transitions.filter { it.outPlaces == Te.inPlaces }
        val TeSplits = petri.transitions.filter { it.inPlaces == Te.outPlaces }
        val Tf = petri.transitions.first { it.name == f.name }
        val TfSplits = petri.transitions.filter { it.inPlaces == Tf.outPlaces }
        val Tg = petri.transitions.first { it.name == g.name }
        val Th = petri.transitions.first { it.name == h.name }
        val Tz = petri.transitions.first { it.name == z.name }
        val TzJoins = petri.transitions.filter { it.outPlaces == Tz.inPlaces }

        assertEquals(2, TaSplits.size)
        assertEquals(2, TbJoins.size)
        assertEquals(2, TcJoins.size)
        assertEquals(2, TdJoins.size)
        assertEquals(2, TeJoins.size)
        assertEquals(3, TeSplits.size)
        assertEquals(2, TfSplits.size)
        assertEquals(2, TzJoins.size)

        assertTrue(TaSplits.any { TaSplit -> TbJoins.any { TbJoin -> TbJoin.inPlaces.first() in TaSplit.outPlaces } })
        assertTrue(TaSplits.any { TaSplit -> TcJoins.any { TcJoin -> TcJoin.inPlaces.first() in TaSplit.outPlaces } })
        assertTrue(TaSplits.all { TaSplit -> TdJoins.any { TdJoin -> TdJoin.inPlaces.first() in TaSplit.outPlaces } })
        assertTrue(TeJoins.any { TeJoin -> Tb.outPlaces.first() in TeJoin.inPlaces })
        assertTrue(TeJoins.any { TeJoin -> Tc.outPlaces.first() in TeJoin.inPlaces })
        assertTrue(TeJoins.all { TeJoin -> Td.outPlaces.first() in TeJoin.inPlaces })
        assertTrue(TeSplits.any { TeSplit -> Tf.inPlaces.first() in TeSplit.outPlaces })
        assertTrue(TeSplits.any { TeSplit -> Tg.inPlaces.first() in TeSplit.outPlaces })
        assertTrue(TeSplits.any { TeSplit -> Th.inPlaces.first() in TeSplit.outPlaces })
        assertTrue(TfSplits.any { TfSplit -> TbJoins.any { TbJoin -> TbJoin.inPlaces.first() in TfSplit.outPlaces } })
        assertTrue(TfSplits.any { TfSplit -> TbJoins.any { TcJoin -> TcJoin.inPlaces.first() in TfSplit.outPlaces } })
        assertTrue(TfSplits.all { TfSplit -> TdJoins.any { TdJoin -> TdJoin.inPlaces.first() in TfSplit.outPlaces } })
        assertTrue(TzJoins.any { TzJoin -> Tg.outPlaces.first() in TzJoin.inPlaces })
        assertTrue(TzJoins.any { TzJoin -> Th.outPlaces.first() in TzJoin.inPlaces })
    }

    @Test
    fun `PM book Fig 3 12 replay as Petri net`() {
        val cnet = constructCnet()
        val petri = cnet.toPetriNet()
        val instance = petri.createInstance()

        val Ta = petri.transitions.first { it.name == a.name }
        val TaSplits = petri.transitions.filter { it.inPlaces == Ta.outPlaces }
        val Tb = petri.transitions.first { it.name == b.name }
        val TbJoins = petri.transitions.filter { it.outPlaces == Tb.inPlaces }
        val Tc = petri.transitions.first { it.name == c.name }
        val TcJoins = petri.transitions.filter { it.outPlaces == Tc.inPlaces }
        val Td = petri.transitions.first { it.name == d.name }
        val TdJoins = petri.transitions.filter { it.outPlaces == Td.inPlaces }
        val Te = petri.transitions.first { it.name == e.name }
        val TeJoins = petri.transitions.filter { it.outPlaces == Te.inPlaces }
        val TeSplits = petri.transitions.filter { it.inPlaces == Te.outPlaces }
        val Tf = petri.transitions.first { it.name == f.name }
        val TfSplits = petri.transitions.filter { it.inPlaces == Tf.outPlaces }
        val Tg = petri.transitions.first { it.name == g.name }
        val Th = petri.transitions.first { it.name == h.name }
        val Tz = petri.transitions.first { it.name == z.name }
        val TzJoins = petri.transitions.filter { it.outPlaces == Tz.inPlaces }

        with(instance) {
            expecting(Ta)[0].execute()
            expecting(*TaSplits.toTypedArray())
                .first { TaSplit -> TcJoins.any { TcJoin -> TcJoin.inPlaces.any { it in TaSplit.activity.outPlaces } } }
                .execute()
            val TcJoin =
                TcJoins.first { TcJoin -> TaSplits.any { TaSplit -> TaSplit.outPlaces.any { it in TcJoin.inPlaces } } }
            val TdJoin =
                TdJoins.first { TdJoin -> TaSplits.any { TaSplit -> TaSplit.outPlaces.any { it in TdJoin.inPlaces } } }
            expecting(TcJoin, TdJoin).first { it.activity.outPlaces == Td.inPlaces }.execute()
            expecting(TcJoin, Td).first { it.activity === Td }.execute()
            expecting(TcJoin)[0].execute()
            expecting(Tc)[0].execute()
            expecting(TeJoins.first { Tc.outPlaces.first() in it.inPlaces })[0].execute()
            expecting(Te)[0].execute()
            expecting(*TeSplits.toTypedArray()).first { it.activity.outPlaces == Tg.inPlaces }.execute()
            expecting(Tg)[0].execute()
            expecting(TzJoins.first { it.inPlaces == Tg.outPlaces })[0].execute()
            expecting(Tz)[0].execute()
            expecting()
        }
    }
}
