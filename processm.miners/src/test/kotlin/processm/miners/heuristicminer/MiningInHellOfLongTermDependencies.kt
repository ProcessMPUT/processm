package processm.miners.heuristicminer

import org.junit.jupiter.api.Test
import processm.core.models.causalnet.*
import processm.core.models.causalnet.mock.Event
import processm.core.models.causalnet.verifier.Verifier
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * A hell of long term dependencies. There are four intended sequences:
 * a b1 c e
 * a b2 c e
 * a b1 b2 c d e
 * a b2 b1 c d e
 *
 * The most important elements are:
 * Join b1, b2, c -> d: to ensure that both b1 and b2 were executed and to ensure that d is executed after c
 * Joins b1, c -> e/b2, c -> e/d -> e: to ensure that d is not skipped if both b1 and b2 were executed
 */
class MiningInHellOfLongTermDependencies {
    val a = Node("a")
    val b1 = Node("b1")
    val b2 = Node("b2")
    val c = Node("c")
    val d = Node("d")
    val e = Node("e")
    val reference: Model by lazy {
        val model = MutableModel(start = a, end = e)
        model.addInstance(a, b1, b2, c, d, e)
        listOf(
            a to b1,
            a to b2,
            b1 to c,
            b2 to c,
            b1 to e,
            b1 to d,
            b2 to d,
            b2 to e,
            c to d,
            c to e,
            d to e
        ).forEach { (a, b) -> model.addDependency(a, b) }
        //splits
        listOf(
            listOf(a to b1),
            listOf(a to b2),
            listOf(a to b1, a to b2),
            listOf(b1 to c, b1 to e),
            listOf(b1 to c, b1 to d),
            listOf(b2 to c, b2 to e),
            listOf(b2 to c, b2 to d),
            listOf(c to e),
            listOf(c to d),
            listOf(d to e)
        )
            .map { it.map { (a, b) -> Dependency(a, b) }.toSet() }
            .forEach { model.addSplit(Split(it)) }
        //joins
        listOf(
            listOf(a to b1),
            listOf(a to b2),
            listOf(b1 to c),
            listOf(b2 to c),
            listOf(b1 to c, b2 to c),
            listOf(b1 to d, b2 to d, c to d),
            listOf(c to e, b1 to e),
            listOf(c to e, b2 to e),
            listOf(d to e)
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
        println(v.validSequences.map { seq -> seq.map { ab -> ab.a }.filter { !it.special }.map { it.activity } }.toSet())
        assertEquals(
            referenceVerifier.validSequences.map { seq -> seq.map { it.a }.filter { !it.special } }.toSet(),
            v.validSequences.map { seq -> seq.map { ab -> ab.a } }.toSet()
        )
        assertFalse(v.hasDeadParts)
        assertTrue(v.isSound)
    }
}