package processm.core.models.causalnet.verifier

import processm.core.models.causalnet.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * A showcase of long term dependency between two pairs of nodes.
 * There is exactly one long-term dependency in this model: (b1&b2)->(d1&d2)
 * In order to avoid decomposition, all activities participating in this dependency must be executable separately, otherwise
 * * If b1 must be always executed with b2, (b1/b2) -> (d1&d2) would be enough
 * * If d1 must be always executed with d2, (b1&b2) -> (d1/d2) would be enough
 * * If b1/b2 must be always executed with d1/d2, it would be possible to decompose the dependency into two, e.g., b1->d1 and b2->d2 (or vice-versa)
 */
class Treachery9thCircleOfHell {

    val a = Node("a")
    val b1 = Node("b1")
    val b2 = Node("b2")
    val c = Node("c")
    val d1 = Node("d1")
    val d2 = Node("d2")
    val e = Node("e")

    val model by lazy {
        val model = MutableModel(start = a, end = e)
        val splits = listOf(
            listOf(a to b1),
            listOf(a to b2),
            listOf(a to b1, a to b2),
            listOf(a to d1),
            listOf(a to d2),
            listOf(b1 to c, b1 to e),
            listOf(b1 to d1, b1 to c, b1 to d2),
            listOf(b2 to c, b2 to e),
            listOf(b2 to d1, b2 to c, b2 to d2),
            listOf(d1 to e),
            listOf(d2 to e),
            listOf(c to d1, c to d2),
            listOf(c to e)
        )
        val joins = listOf(
            listOf(a to b1),
            listOf(a to b2),
            listOf(b1 to c),
            listOf(b2 to c),
            listOf(b1 to c, b2 to c),
            listOf(a to d1),
            listOf(b1 to d1, b2 to d1, c to d1),
            listOf(a to d2),
            listOf(b1 to d2, b2 to d2, c to d2),
            listOf(b1 to e, c to e),
            listOf(b2 to e, c to e),
            listOf(d1 to e, d2 to e),
            listOf(d1 to e),
            listOf(d2 to e)
        )
        model.addInstance(a, b1, b2, c, d1, d2, e)
        splits
            .map { deps ->
                deps.map { (a, b) -> Dependency(a, b) }
            }.forEach { deps ->
                deps.forEach { d -> model.addDependency(d) }
                model.addSplit(Split(deps.toSet()))
            }
        joins
            .map { deps ->
                deps.map { (a, b) -> Dependency(a, b) }
            }.forEach { deps ->
                deps.forEach { d -> model.addDependency(d) }
                model.addJoin(Join(deps.toSet()))
            }
        model
    }

    @Test
    fun test() {
        val v = Verifier(model)
        assertEquals(
            setOf(
                listOf(a, d1, e),
                listOf(a, d2, e),
                listOf(a, b1, c, e),
                listOf(a, b2, c, e),
                listOf(a, b1, b2, c, d1, d2, e),
                listOf(a, b1, b2, c, d2, d1, e),
                listOf(a, b2, b1, c, d1, d2, e),
                listOf(a, b2, b1, c, d2, d1, e)
            ), v.validSequences.map { seq -> seq.map { it.a } }.toSet()
        )
        assertFalse { v.hasDeadParts }
        assertTrue { v.isSound }
    }
}