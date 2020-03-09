package processm.miners.heuristicminer

import processm.core.logging.logger
import processm.core.models.causalnet.Model
import processm.core.models.causalnet.Node
import processm.core.models.causalnet.causalnet
import processm.core.models.causalnet.mock.Event
import processm.core.models.causalnet.verifier.Verifier
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FromModelToLogAndBackAgain {
    val a = Node("a")
    val b = Node("b")
    val b1 = Node("b1")
    val b2 = Node("b2")
    val c = Node("c")
    val d = Node("d")
    val d1 = Node("d2")
    val d2 = Node("d1")
    val e = Node("e")

    private fun test(reference: Model) {
        val referenceVerifier = Verifier(reference)
        val log: Log = referenceVerifier
            .validSequences
            .flatMap { seq -> List(1) { seq.asSequence().map { ab -> Event(ab.a.activity) } }.asSequence() }
        log.forEach { println(it.toList()) }
        val hm = HeuristicMiner(log)
        val v = Verifier(hm.result)
        val expectedSequences =
            referenceVerifier.validSequences.map { seq -> seq.map { it.a }.filter { !it.special } }.toSet()
        val actualSequences = v.validSequences.map { seq -> seq.map { ab -> ab.a }.filter { !it.special } }.toSet()
        logger().debug("MODEL:\n" + hm.result)
        logger().debug("EXPECTED SEQUENCES: " + expectedSequences.map { seq -> seq.map { it.activity } })
        logger().debug("ACTUAL SEQUENCES: " + actualSequences.map { seq -> seq.map { it.activity } })
        assertEquals(expectedSequences, actualSequences)
        assertFalse(v.hasDeadParts)
        assertTrue(v.isSound)
    }

    @Test
    fun `hell of long-term dependencies`() {
        test(causalnet {
            start = a
            end = e
            a splits b1 or b2 or b1 + b2
            b1 splits c + e or c + d
            b2 splits c + e or c + d
            c splits d or e
            d splits e
            a joins b1
            a joins b2
            b1 or b2 or b1 + b2 join c
            b1 + b2 + c join d
            c + b1 or c + b2 or d join e
        })
    }

    @Test
    fun `reversed hell of long-term dependencies`() {
        test(causalnet {
            start = a
            end = e
            a splits c + d1 or b or c + d2
            b splits d1 + c + d2
            c splits d1 or d2 or d1 + d2
            d1 splits e
            d2 splits e
            a joins b
            a or b join c
            b + c or a + c join d1
            b + c or a + c join d2
            d1 or d2 or d1 + d2 join e
        })
    }

    @Test
    fun `treachery 9th circle of hell`() {
        test(causalnet {
            start = a
            end = e
            a splits b1 or b2 or b1 + b2 or d1 or d2
            b1 splits c + e or d1 + c + d2
            b2 splits c + e or d1 + c + d2
            d1 splits e
            d2 splits e
            c splits d1 + d2 or e
            a joins b1
            a joins b2
            b1 or b2 or b1 + b2 join c
            a or b1 + b2 + c join d1
            a or b1 + b2 + c join d2
            b1 + c or b2 + c or d1 + d2 or d1 or d2 join e
        })
    }

    @Test
    fun `flexible fuzzy miner long-term dependency example`() {
        test(causalnet {
            start = a
            end = e
            a splits b1 or b2
            b1 splits c + d1
            b2 splits c + d2
            c splits d1 or d2
            d1 splits e
            d2 splits e
            a joins b1
            a joins b2
            b1 or b2 join c
            b1 + c join d1
            b2 + c join d2
            d1 or d2 join e
        })
    }
}