package processm.experimental.miners.causalnet.heuristicminer

import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import processm.core.helpers.mapToSet
import processm.core.log.Helpers.event
import processm.core.log.hierarchical.Log
import processm.core.log.hierarchical.Trace
import processm.core.models.causalnet.Dependency
import processm.core.models.causalnet.Join
import processm.core.models.causalnet.Node
import processm.core.models.causalnet.Split
import processm.experimental.miners.causalnet.heuristicminer.traceregisters.CompleteTraceRegister
import processm.experimental.miners.causalnet.heuristicminer.traceregisters.SingleShortestTraceRegister
import processm.miners.causalnet.CausalNetMiner
import processm.miners.causalnet.heuristicminer.OfflineHeuristicMiner
import processm.miners.causalnet.heuristicminer.bindingproviders.CompleteBindingProvider
import processm.miners.causalnet.heuristicminer.bindingproviders.hypothesisselector.MostParsimoniousHypothesisSelector
import processm.miners.causalnet.heuristicminer.bindingselectors.CountGroups
import processm.miners.causalnet.heuristicminer.bindingselectors.CountSeparately
import processm.miners.causalnet.heuristicminer.dependencygraphproviders.DefaultDependencyGraphProvider
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

    @Suppress("unused")
    companion object {
        @JvmStatic
        fun hmFactory(minDirectlyFollows: Int, minDependency: Double): List<CausalNetMiner> =
            listOf(
                OnlineHeuristicMiner(
                    dependencyGraphProvider = DefaultDependencyGraphProvider(
                        minDirectlyFollows,
                        minDependency
                    )
                ),
                OnlineHeuristicMiner(
                    dependencyGraphProvider = DefaultDependencyGraphProvider(
                        minDirectlyFollows,
                        minDependency
                    ), traceRegister = SingleShortestTraceRegister()
                ),
                OnlineHeuristicMiner(
                    dependencyGraphProvider = DefaultDependencyGraphProvider(
                        minDirectlyFollows,
                        minDependency
                    ), traceRegister = CompleteTraceRegister()
                ),
                OfflineHeuristicMiner(
                    dependencyGraphProvider = DefaultDependencyGraphProvider(
                        minDirectlyFollows,
                        minDependency
                    )
                ),
                OfflineHeuristicMiner(
                    dependencyGraphProvider = DefaultDependencyGraphProvider(minDirectlyFollows, minDependency),
                    splitSelector = CountGroups(1),
                    joinSelector = CountGroups(1)
                )
            )

        @JvmStatic
        fun hmFactory(): List<CausalNetMiner> = hmFactory(1, 1e-5)

        @JvmStatic
        fun hmFactory_5_9(): List<CausalNetMiner> =
            hmFactory(5, .9)

        @JvmStatic
        fun hmFactory_2_7(): List<CausalNetMiner> =
            listOf(
                OnlineHeuristicMiner(
                    minBindingSupport = 4,
                    dependencyGraphProvider = DefaultDependencyGraphProvider(2, .7),
                    bindingProvider = CompleteBindingProvider(MostParsimoniousHypothesisSelector())
                ),
                OfflineHeuristicMiner(
                    splitSelector = CountSeparately(4),
                    joinSelector = CountSeparately(4),
                    dependencyGraphProvider = DefaultDependencyGraphProvider(2, .7),
                    bindingProvider = CompleteBindingProvider(MostParsimoniousHypothesisSelector())
                )
            )
    }

    internal infix fun <A, B> Collection<A>.times(right: Collection<B>): List<Pair<A, B>> =
        this.flatMap { a -> right.map { b -> a to b } }


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
            assertEquals(nodes.toSet(), instances.filter { !it.isArtificial }.toSet())
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
    fun `dependency graph minDirectlyFollows=5 minDependency=,9`(hm: CausalNetMiner) {
        hm.processLog(log)
        with(hm.result) {
            assertEquals(nodes.toSet(), instances.filter { !it.isArtificial }.toSet())
            with(outgoing) {
                assertEquals(setOf(b, c, d), getValue(a).mapToSet { d -> d.target })
                assertEquals(setOf(e), getValue(b).mapToSet { d -> d.target })
                assertEquals(setOf(e), getValue(c).mapToSet { d -> d.target })
                assertEquals(setOf(e), getValue(d).mapToSet { d -> d.target })
                assertEquals(setOf(end), getValue(e).mapToSet { d -> d.target })
            }
            with(incoming) {
                assertEquals(setOf(start), getValue(a).mapToSet { d -> d.source })
                assertEquals(setOf(a), getValue(b).mapToSet { d -> d.source })
                assertEquals(setOf(a), getValue(c).mapToSet { d -> d.source })
                assertEquals(setOf(a), getValue(d).mapToSet { d -> d.source })
                assertEquals(setOf(b, c, d), getValue(e).mapToSet { d -> d.source })
            }
        }
    }
}
