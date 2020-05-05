package processm.miners.heuristicminer

import io.mockk.every
import io.mockk.mockk
import processm.core.helpers.mapToSet
import processm.core.log.Event
import processm.core.log.hierarchical.Log
import processm.core.log.hierarchical.Trace
import processm.core.logging.logger
import processm.core.models.causalnet.CausalNet
import processm.core.models.causalnet.Node
import processm.core.verifiers.CausalNetVerifier
import kotlin.test.assertEquals
import kotlin.test.assertTrue

object Helper {

    fun str(inp: Set<List<Node>>): String {
        return inp.map { seq -> seq.map { it.activity } }.toString()
    }

    fun logFromModel(model: CausalNet): Log {
        val tmp = CausalNetVerifier().verify(model).validLoopFreeSequences.map { seq -> seq.map { it.a } }
            .toSet()
        return Log(tmp.map { seq -> Trace(seq.asSequence().map { event(it.activity) }) }.asSequence())
    }

    fun logFromString(text: String): Log =
        Log(
            text.split('\n')
                .map { line -> Trace(line.split(" ").filter { it.isNotEmpty() }.map { event(it) }.asSequence()) }
                .asSequence()
        )

    fun compareWithReference(reference: CausalNet, miner: (Log) -> CausalNet) {
        logger().debug("REFERENCE:\n${reference}")
        val referenceVerifier = CausalNetVerifier().verify(reference)
        val expectedSequences =
            referenceVerifier.validSequences.mapToSet { seq -> seq.map { it.a }.filter { !it.special } }
        logger().debug("EXPECTED SEQUENCES: ${str(expectedSequences)}")
        assertTrue(referenceVerifier.noDeadParts)
        assertTrue(referenceVerifier.isSound)
        val log = Log(referenceVerifier
            .validSequences
            .map { seq -> Trace(seq.asSequence().map { ab -> event(ab.a.activity) }) })
        log.traces.forEach { println(it.events.toList()) }
        val minedModel = miner(log)
        logger().debug("~~~~~~~~~~~~~~~MINED~~~~~~~~~~~~~~~~\n$minedModel~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~")
        val v = CausalNetVerifier().verify(minedModel)
        val actualSequences = v.validSequences.mapToSet { seq -> seq.map { ab -> ab.a }.filter { !it.special } }
        logger().debug("ACTUAL SEQUENCES: ${str(actualSequences)}")
        logger().debug("UNEXPECTED SEQUENCES: ${str(actualSequences - expectedSequences)}")
        logger().debug("MISSING SEQUENCES: ${str(expectedSequences - actualSequences)}")
        logger().debug("MODEL:\n" + minedModel)
        assertEquals(expectedSequences, actualSequences)
        assertTrue(v.noDeadParts)
        assertTrue(v.isSound)
    }

    fun event(name: String): Event {
        val e = mockk<Event>()
        every { e.conceptName } returns name
        every { e.conceptInstance } returns null
        every { e.lifecycleTransition } returns "complete"
        every { e.hashCode() } returns name.hashCode()
        every { e.toString() } returns name
        return e
    }
}