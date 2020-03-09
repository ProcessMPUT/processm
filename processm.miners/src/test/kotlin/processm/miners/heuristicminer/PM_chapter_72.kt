package processm.miners.heuristicminer

import processm.core.models.causalnet.Dependency
import processm.core.models.causalnet.Join
import processm.core.models.causalnet.Node
import processm.core.models.causalnet.Split
import processm.core.models.causalnet.mock.Event
import kotlin.math.absoluteValue
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
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
    private val log = listOf(
        "ae" to 5,
        "abce" to 10,
        "acbe" to 10,
        "abe" to 1,
        "ace" to 1,
        "ade" to 10,
        "adde" to 2,
        "addde" to 1
    ).asSequence()
        .flatMap { (s, n) -> List(n) { s.map { e -> Event(e.toString()) }.asSequence() }.asSequence() }

    @Test
    fun `directly follows`() {
        val hm = HeuristicMiner(log)
        assertEquals(
            mapOf(
                (hm.start to a) to 5 + 10 + 10 + 1 + 1 + 10 + 2 + 1,
                (e to hm.end) to 5 + 10 + 10 + 1 + 1 + 10 + 2 + 1,
                (a to b) to 11,
                (a to c) to 11,
                (a to d) to 13,
                (a to e) to 5,
                (b to c) to 10,
                (b to e) to 11,
                (c to b) to 10,
                (c to e) to 11,
                (d to d) to 4,
                (d to e) to 13
            ), hm.directlyFollows
        )
    }

    @Test
    fun `dependency measure`() {
        val hm = HeuristicMiner(log)
        val dm = listOf(
            listOf(0.0, 0.92, 0.92, 0.93, 0.83),
            listOf(-0.92, 0.0, 0.0, 0.0, 0.92),
            listOf(-0.92, 0.0, 0.0, 0.0, 0.92),
            listOf(-0.93, 0.0, 0.0, 0.8, 0.93),
            listOf(-0.83, -0.92, -0.92, -0.93, 0.0)
        )
        val indices = nodes.indices.map { it }
        (indices times indices).forEach { (i, j) ->
            assertDoubleEquals(hm.dependency[nodes[i] to nodes[j]], dm[i][j], 0.01)
        }
    }

    @Test
    fun `minDirectlyFollows=2 minDependency=,7 Fig 7_6`() {
        val hm = HeuristicMiner(log, 2, .7, 4)
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

    @Test
    fun `dependency graph minDirectlyFollows=5 minDependency=,9`() {
        val hm = HeuristicMiner(log, 5, .9)
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