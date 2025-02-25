package processm.miners.causalnet.heuristicminer

import processm.core.log.Helpers.assertDoubleEquals
import processm.core.log.Helpers.event
import processm.core.log.hierarchical.Log
import processm.core.log.hierarchical.Trace
import processm.core.models.causalnet.Dependency
import processm.core.models.causalnet.Join
import processm.core.models.causalnet.Node
import processm.core.models.causalnet.Split
import processm.helpers.mapToSet
import processm.miners.causalnet.heuristicminer.bindingproviders.CompleteBindingProvider
import processm.miners.causalnet.heuristicminer.bindingproviders.hypothesisselector.MostParsimoniousHypothesisSelector
import processm.miners.causalnet.heuristicminer.bindingselectors.CountSeparately
import processm.miners.causalnet.heuristicminer.dependencygraphproviders.DefaultDependencyGraphProvider
import processm.miners.causalnet.onlineminer.BasicTraceToNodeTrace
import kotlin.test.Test
import kotlin.test.assertEquals

class PM_chapter_72 {

    private val a = Node("a")
    private val b = Node("b")
    private val c = Node("c")
    private val d = Node("d")
    private val e = Node("e")
    private val nodes = listOf(a, b, c, d, e)
    private val log = Log(listOf(
        "ae" to 5,
        "abce" to 10,
        "acbe" to 10,
        "abe" to 1,
        "ace" to 1,
        "ade" to 10,
        "adde" to 2,
        "addde" to 1
    ).asSequence()
        .flatMap { (s, n) -> List(n) { Trace(s.map { e -> event(e.toString()) }.asSequence()) }.asSequence() })


    @Test
    fun `directly follows`() {
        val gp = DefaultDependencyGraphProvider(1, 1e-5)
        for (trace in log.traces)
            gp.processTrace(BasicTraceToNodeTrace()(trace))
        assertEquals(
            mapOf(
                Dependency(gp.start, a) to 5 + 10 + 10 + 1 + 1 + 10 + 2 + 1,
                Dependency(e, gp.end) to 5 + 10 + 10 + 1 + 1 + 10 + 2 + 1,
                Dependency(a, b) to 11,
                Dependency(a, c) to 11,
                Dependency(a, d) to 13,
                Dependency(a, e) to 5,
                Dependency(b, c) to 10,
                Dependency(b, e) to 11,
                Dependency(c, b) to 10,
                Dependency(c, e) to 11,
                Dependency(d, d) to 4,
                Dependency(d, e) to 13
            ), gp.directlyFollows
        )
    }

    internal infix fun <A, B> Collection<A>.times(right: Collection<B>): List<Pair<A, B>> =
        this.flatMap { a -> right.map { b -> a to b } }

    @Test
    fun `dependency measure`() {
        val gp = DefaultDependencyGraphProvider(1, 1e-5)
        for (trace in log.traces)
            gp.processTrace(BasicTraceToNodeTrace()(trace))
        val dm = listOf(
            listOf(0.0, 0.92, 0.92, 0.93, 0.83),
            listOf(-0.92, 0.0, 0.0, 0.0, 0.92),
            listOf(-0.92, 0.0, 0.0, 0.0, 0.92),
            listOf(-0.93, 0.0, 0.0, 0.8, 0.93),
            listOf(-0.83, -0.92, -0.92, -0.93, 0.0)
        )
        val indices = nodes.indices.map { it }
        (indices times indices).forEach { (i, j) ->
            assertDoubleEquals(gp.dependency(nodes[i], nodes[j]), dm[i][j], 0.01)
        }
    }

    @Test
    fun `minDirectlyFollows=2 minDependency=,7 Fig 7_6`() {
        val hm = OfflineHeuristicMiner(
            splitSelector = CountSeparately(4),
            joinSelector = CountSeparately(4),
            dependencyGraphProvider = DefaultDependencyGraphProvider(2, .7),
            bindingProvider = CompleteBindingProvider(MostParsimoniousHypothesisSelector())
        )
        hm.processLog(log)
        println(hm.result)
        with(hm.result) {
            assertEquals(nodes.toSet(), instances.filterTo(HashSet()) { !it.isSilent })
            with(outgoing) {
                assertEquals(setOf(b, c, d, e), getValue(a).mapToSet { d -> d.target })
                assertEquals(setOf(e), getValue(b).mapToSet { d -> d.target })
                assertEquals(setOf(e), getValue(c).mapToSet { d -> d.target })
                assertEquals(setOf(d, e), getValue(d).mapToSet { d -> d.target })
                assertEquals(setOf(end), getValue(e).mapToSet { d -> d.target })
            }
            with(incoming) {
                assertEquals(setOf(start), getValue(a).mapToSet { d -> d.source })
                assertEquals(setOf(a), getValue(b).mapToSet { d -> d.source })
                assertEquals(setOf(a), getValue(c).mapToSet { d -> d.source })
                assertEquals(setOf(a, d), getValue(d).mapToSet { d -> d.source })
                assertEquals(setOf(b, c, d, a), getValue(e).mapToSet { d -> d.source })
            }
            with(joins) {
                assertEquals(listOf(Join(setOf(Dependency(a, b)))), getValue(b))
                assertEquals(listOf(Join(setOf(Dependency(a, c)))), getValue(c))
                assertEquals(
                    setOf(
                        Join(setOf(Dependency(a, d))),
                        Join(setOf(Dependency(d, d)))
                    ), getValue(d).toSet()
                )
                assertEquals(
                    setOf(
                        Join(setOf(Dependency(a, e))),
                        Join(setOf(Dependency(b, e), Dependency(c, e))),
                        Join(setOf(Dependency(d, e)))
                    ), getValue(e).toSet()
                )
            }
            with(splits) {
                assertEquals(
                    setOf(
                        Split(setOf(Dependency(a, e))),
                        Split(setOf(Dependency(a, b), Dependency(a, c))),
                        Split(setOf(Dependency(a, d)))
                    ), getValue(a).toSet()
                )
                assertEquals(listOf(Split(setOf(Dependency(b, e)))), getValue(b))
                assertEquals(listOf(Split(setOf(Dependency(c, e)))), getValue(c))
                assertEquals(
                    setOf(
                        Split(setOf(Dependency(d, e))),
                        Split(setOf(Dependency(d, d)))
                    ), getValue(d).toSet()
                )
            }
        }
    }
}
