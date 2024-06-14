package processm.experimental.miners.causalnet.heuristicminer

import processm.core.log.Helpers.event
import processm.core.log.hierarchical.Log
import processm.core.log.hierarchical.Trace
import processm.core.models.causalnet.Dependency
import processm.core.models.causalnet.Join
import processm.core.models.causalnet.Node
import processm.core.models.causalnet.Split
import processm.miners.causalnet.heuristicminer.bindingproviders.CompleteBindingProvider
import processm.miners.causalnet.heuristicminer.bindingproviders.hypothesisselector.MostGreedyHypothesisSelector
import kotlin.test.Ignore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * This tests a model with the following two acceptable sequences:
 * a->b1->c->d1->e
 * a->b2->c->d2->e
 * I.e., there is a long-term dependency between b1-d1 and b2-d2.
 * Any other sequence is violation of the process and should not be allowed by the mined model.
 */
class LongTermDependencies {

    private val a = Node("a")
    private val b1 = Node("b1")
    private val b2 = Node("b2")
    private val c = Node("c")
    private val d1 = Node("d1")
    private val d2 = Node("d2")
    private val e = Node("e")
    private val log = Log(listOf(
        listOf(a, b1, c, d1, e) to 100,
        listOf(a, b2, c, d2, e) to 100
    ).asSequence()
        .flatMap { (trace, n) ->
            List(n) {
                Trace(trace.map { n -> event(n.activity) }.asSequence())
            }.asSequence()
        })
    private val hm = OnlineHeuristicMiner(1, bindingProvider = CompleteBindingProvider(MostGreedyHypothesisSelector()))

    init {
        hm.processLog(log)
    }

    @Test
    fun nodes() {
        assertEquals(setOf(a, b1, b2, c, d1, d2, e), hm.result.instances.filterTo(HashSet()) { n -> !n.isSilent })
    }

    @Test
    fun `short term splits`() {
        with(hm.result.splits) {
            assertEquals(
                listOf(
                    Split(setOf(Dependency(a, b1))),
                    Split(setOf(Dependency(a, b2)))
                ), getValue(a)
            )
            //b1 and  b2 should contain a long-term split
            assertEquals(
                listOf(
                    Split(setOf(Dependency(c, d1))),
                    Split(setOf(Dependency(c, d2)))
                ), getValue(c)
            )
            assertEquals(listOf(Split(setOf(Dependency(d1, e)))), getValue(d1))
            assertEquals(listOf(Split(setOf(Dependency(d2, e)))), getValue(d2))
        }
    }

    @Ignore("See #97")
    @Test
    fun `long term dependencies`() {
        assertTrue { hm.result.outgoing.getValue(b1).contains(Dependency(b1, d1)) }
        assertTrue { hm.result.outgoing.getValue(b2).contains(Dependency(b2, d2)) }
    }

    @Ignore("See #97")
    @Test
    fun `long term splits`() {
        with(hm.result.splits) {
            assertEquals(listOf(Split(setOf(Dependency(b1, c), Dependency(b1, d1)))), getValue(b1))
            assertEquals(listOf(Split(setOf(Dependency(b2, c), Dependency(b2, d2)))), getValue(b2))
        }
    }

    @Ignore("See #97")
    @Test
    fun `long term joins`() {
        with(hm.result.joins) {
            assertEquals(listOf(Join(setOf(Dependency(c, d1), Dependency(b1, d1)))), getValue(d1))
            assertEquals(listOf(Join(setOf(Dependency(c, d2), Dependency(b2, d2)))), getValue(d2))
        }
    }

    @Test
    fun `single start`() {
        val m = hm.result
        assertEquals(1, m.instances.filter { n -> m.incoming[n].isNullOrEmpty() }.count())
    }

    @Test
    fun `single end`() {
        val m = hm.result
        assertEquals(1, m.instances.filter { n -> m.outgoing[n].isNullOrEmpty() }.count())
    }

    @Test
    fun `short term joins`() {
        with(hm.result.joins) {
            assertEquals(
                listOf(
                    Join(setOf(Dependency(b1, c))),
                    Join(setOf(Dependency(b2, c)))
                ), getValue(c)
            )
            //d1 and  d2 should contain a long-term join
            assertEquals(
                listOf(
                    Join(setOf(Dependency(d1, e))),
                    Join(setOf(Dependency(d2, e)))
                ), getValue(e)
            )
            assertEquals(listOf(Join(setOf(Dependency(a, b1)))), getValue(b1))
            assertEquals(listOf(Join(setOf(Dependency(a, b2)))), getValue(b2))
        }
    }
}
