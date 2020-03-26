package processm.miners.heuristicminer

import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import processm.core.log.hierarchical.Log
import processm.core.log.hierarchical.Trace
import processm.core.models.causalnet.Dependency
import processm.core.models.causalnet.Join
import processm.core.models.causalnet.Node
import processm.core.models.causalnet.Split
import processm.miners.heuristicminer.Helper.event
import processm.miners.heuristicminer.bindingproviders.CompleteBindingProvider
import processm.miners.heuristicminer.bindingproviders.hypothesisselector.MostParsimoniousHypothesisSelector
import processm.miners.heuristicminer.bindingselectors.CountGroups
import processm.miners.heuristicminer.traceregisters.CompleteTraceRegister
import processm.miners.heuristicminer.traceregisters.SingleShortestTraceRegister
import kotlin.math.absoluteValue
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

fun assertDoubleEquals(expected: Double?, actual: Double?, eps: Double = 1e-5) {
    if (expected == null || actual == null)
        assertEquals(expected, actual)
    else {
        assertTrue { (expected.absoluteValue <= eps && actual.absoluteValue <= eps) || ((actual / expected).absoluteValue - 1).absoluteValue <= eps }
    }
}

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

    @Suppress("unused")
    companion object {
        @JvmStatic
        fun hmFactory(minDirectlyFollows: Int, minDependency: Double): List<AbstractHeuristicMiner> =
            listOf(
                OnlineHeuristicMiner(minDirectlyFollows, minDependency),
                OnlineHeuristicMiner(minDirectlyFollows, minDependency, traceRegister = SingleShortestTraceRegister()),
                OnlineHeuristicMiner(minDirectlyFollows, minDependency, traceRegister = CompleteTraceRegister()),
                OfflineHeuristicMiner(minDirectlyFollows, minDependency),
                OfflineHeuristicMiner(
                    minDirectlyFollows,
                    minDependency,
                    splitSelector = CountGroups(1),
                    joinSelector = CountGroups(1)
                )
            )

        @JvmStatic
        fun hmFactory(): List<AbstractHeuristicMiner> = hmFactory(1, 1e-5)

        @JvmStatic
        fun hmFactory_5_9(): List<AbstractHeuristicMiner> =
            hmFactory(5, .9)

        @JvmStatic
        fun hmFactory_2_7(): List<AbstractHeuristicMiner> =
            listOf(
                OnlineHeuristicMiner(
                    2,
                    .7,
                    4,
                    bindingProvider = CompleteBindingProvider(MostParsimoniousHypothesisSelector())
                ),
                OfflineHeuristicMiner(
                    2,
                    .7,
                    4,
                    bindingProvider = CompleteBindingProvider(MostParsimoniousHypothesisSelector())
                )
            )
    }


    @ParameterizedTest
    @MethodSource("hmFactory")
    fun `directly follows`(hm: AbstractHeuristicMiner) {
        hm.processLog(log)
        assertEquals(
            mapOf(
                Dependency(hm.start, a) to 5 + 10 + 10 + 1 + 1 + 10 + 2 + 1,
                Dependency(e, hm.end) to 5 + 10 + 10 + 1 + 1 + 10 + 2 + 1,
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
            ), hm.directlyFollows
        )
    }

    internal infix fun <A, B> Collection<A>.times(right: Collection<B>): List<Pair<A, B>> =
        this.flatMap { a -> right.map { b -> a to b } }

    @ParameterizedTest
    @MethodSource("hmFactory")
    fun `dependency measure`(hm: AbstractHeuristicMiner) {
        hm.processLog(log)
        val dm = listOf(
            listOf(0.0, 0.92, 0.92, 0.93, 0.83),
            listOf(-0.92, 0.0, 0.0, 0.0, 0.92),
            listOf(-0.92, 0.0, 0.0, 0.0, 0.92),
            listOf(-0.93, 0.0, 0.0, 0.8, 0.93),
            listOf(-0.83, -0.92, -0.92, -0.93, 0.0)
        )
        val indices = nodes.indices.map { it }
        (indices times indices).forEach { (i, j) ->
            assertDoubleEquals(hm.dependency(nodes[i], nodes[j]), dm[i][j], 0.01)
        }
    }

    @Test
    fun `minDirectlyFollows=2 minDependency=,7 Fig 7_6`() {
        val hm = OfflineHeuristicMiner(
            2, .7, 4,
            bindingProvider = CompleteBindingProvider(MostParsimoniousHypothesisSelector())
        )
        hm.processLog(log)
        println(hm.result)
        with(hm.result) {
            assertEquals(nodes.toSet(), instances.filter { !it.special }.toSet())
            with(outgoing) {
                assertEquals(setOf(b, c, d, e), getValue(a).map { d -> d.target }.toSet())
                assertEquals(setOf(e), getValue(b).map { d -> d.target }.toSet())
                assertEquals(setOf(e), getValue(c).map { d -> d.target }.toSet())
                assertEquals(setOf(d, e), getValue(d).map { d -> d.target }.toSet())
                assertEquals(setOf(end), getValue(e).map { d -> d.target }.toSet())
            }
            with(incoming) {
                assertEquals(setOf(start), getValue(a).map { d -> d.source }.toSet())
                assertEquals(setOf(a), getValue(b).map { d -> d.source }.toSet())
                assertEquals(setOf(a), getValue(c).map { d -> d.source }.toSet())
                assertEquals(setOf(a, d), getValue(d).map { d -> d.source }.toSet())
                assertEquals(setOf(b, c, d, a), getValue(e).map { d -> d.source }.toSet())
            }
            with(joins) {
                assertEquals(setOf(Join(setOf(Dependency(a, b)))), getValue(b))
                assertEquals(setOf(Join(setOf(Dependency(a, c)))), getValue(c))
                assertEquals(
                    setOf(
                        Join(setOf(Dependency(a, d))),
                        Join(setOf(Dependency(d, d)))
                    ), getValue(d)
                )
                assertEquals(
                    setOf(
                        Join(setOf(Dependency(a, e))),
                        Join(setOf(Dependency(b, e), Dependency(c, e))),
                        Join(setOf(Dependency(d, e)))
                    ), getValue(e)
                )
            }
            with(splits) {
                assertEquals(
                    setOf(
                        Split(setOf(Dependency(a, e))),
                        Split(setOf(Dependency(a, b), Dependency(a, c))),
                        Split(setOf(Dependency(a, d)))
                    ), getValue(a)
                )
                assertEquals(setOf(Split(setOf(Dependency(b, e)))), getValue(b))
                assertEquals(setOf(Split(setOf(Dependency(c, e)))), getValue(c))
                assertEquals(
                    setOf(
                        Split(setOf(Dependency(d, e))),
                        Split(setOf(Dependency(d, d)))
                    ), getValue(d)
                )
            }
        }
    }

    @ParameterizedTest
    @MethodSource("hmFactory_5_9")
    fun `dependency graph minDirectlyFollows=5 minDependency=,9`(hm: AbstractHeuristicMiner) {
        hm.processLog(log)
        with(hm.result) {
            assertEquals(nodes.toSet(), instances.filter { !it.special }.toSet())
            with(outgoing) {
                assertEquals(setOf(b, c, d), getValue(a).map { d -> d.target }.toSet())
                assertEquals(setOf(e), getValue(b).map { d -> d.target }.toSet())
                assertEquals(setOf(e), getValue(c).map { d -> d.target }.toSet())
                assertEquals(setOf(e), getValue(d).map { d -> d.target }.toSet())
                assertEquals(setOf(end), getValue(e).map { d -> d.target }.toSet())
            }
            with(incoming) {
                assertEquals(setOf(start), getValue(a).map { d -> d.source }.toSet())
                assertEquals(setOf(a), getValue(b).map { d -> d.source }.toSet())
                assertEquals(setOf(a), getValue(c).map { d -> d.source }.toSet())
                assertEquals(setOf(a), getValue(d).map { d -> d.source }.toSet())
                assertEquals(setOf(b, c, d), getValue(e).map { d -> d.source }.toSet())
            }
        }
    }
}