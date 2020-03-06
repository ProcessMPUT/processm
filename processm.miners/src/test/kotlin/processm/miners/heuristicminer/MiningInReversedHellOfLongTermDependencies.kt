package processm.miners.heuristicminer

import org.junit.jupiter.api.Test
import processm.core.models.causalnet.*
import processm.core.models.causalnet.mock.Event
import processm.core.models.causalnet.verifier.Verifier
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * A reversed hell of long term dependencies. There are four intended sequences:
 * a c d1 e
 * a c d2 e
 * a b c d1 d2 e
 * a b c d2 d1 e
 */
class MiningInReversedHellOfLongTermDependencies {
    val a = Node("a")
    val b = Node("b")
    val c = Node("c")
    val d1 = Node("d1")
    val d2 = Node("d2")
    val e = Node("e")
    val reference: Model by lazy {
        val model = MutableModel(start = a, end = e)
        model.addInstance(a, b, c, d1, d2, e)
        listOf(
            a to d1,
            a to b,
            a to c,
            a to d2,
            b to d1,
            b to c,
            b to d2,
            c to d1,
            c to d2,
            d1 to e,
            d2 to e
        ).forEach { (a, b) -> model.addDependency(a, b) }
        //splits
        listOf(
            listOf(a to d1, a to c),
            listOf(a to b),
            listOf(a to c, a to d2),
            listOf(b to d1, b to c, b to d2),
            listOf(c to d1),
            listOf(c to d2),
            listOf(c to d1, c to d2),
            listOf(d1 to e),
            listOf(d2 to e)
        )
            .map { it.map { (a, b) -> Dependency(a, b) }.toSet() }
            .forEach { model.addSplit(Split(it)) }
        //joins
        listOf(
            listOf(a to b),
            listOf(b to c),
            listOf(a to c),
            listOf(b to d1, c to d1),
            listOf(a to d1, c to d1),
            listOf(c to d2, b to d2),
            listOf(c to d2, a to d2),
            listOf(d1 to e),
            listOf(d2 to e),
            listOf(d1 to e, d2 to e)
        )
            .map { it.map { (a, b) -> Dependency(a, b) }.toSet() }
            .forEach { model.addJoin(Join(it)) }
        model
    }
    val referenceVerifier = Verifier(reference)
    val log: Log = referenceVerifier
        .validSequences
        .flatMap { seq -> List(1) { seq.asSequence().map { ab -> Event(ab.a.activity) } }.asSequence() }


    @Test
    fun test() {
        log.forEach { println(it.toList()) }
        val hm = HeuristicMiner(log)
        val v = Verifier(hm.result)
        println(hm.result)
        println(v.validSequences.map { seq ->
            seq.map { ab -> ab.a }.filter { !it.special }.map { it.activity }
        }.toSet())
        assertEquals(
            referenceVerifier.validSequences.map { seq -> seq.map { it.a }.filter { !it.special } }.toSet(),
            v.validSequences.map { seq -> seq.map { ab -> ab.a } }.toSet()
        )
        assertFalse(v.hasDeadParts)
        assertTrue(v.isSound)
    }
}