package processm.miners.heuristicminer

import processm.core.log.hierarchical.Log
import processm.core.log.hierarchical.Trace
import processm.core.logging.logger
import processm.core.models.causalnet.Model
import processm.core.models.causalnet.Node
import processm.core.verifiers.CausalNetVerifier
import processm.miners.heuristicminer.longdistance.LongDistanceDependencyMiner
import kotlin.test.assertEquals
import kotlin.test.assertTrue

object Helper {

    fun str(inp: Set<List<Node>>): String {
        return inp.map { seq -> seq.map { it.activity } }.toString()
    }

    fun online(log: Log): Model {
        val hm = HeuristicMiner()
        hm.processLog(log)
        return hm.result
    }

    fun offline(log: Log): Model {
        return OfflineHeuristicMiner(log).result
    }

    fun offline(longDistanceDependencyMiner: LongDistanceDependencyMiner): (Log) -> Model {
        return { log -> OfflineHeuristicMiner(log, longDistanceDependencyMiner = longDistanceDependencyMiner).result }
    }

    fun logFromModel(model: Model): Log {
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

    fun compareWithReference(reference: Model, miner: (Log) -> Model) {
        logger().debug("REFERENCE:\n${reference}")
        val referenceVerifier = CausalNetVerifier().verify(reference)
        val expectedSequences =
            referenceVerifier.validSequences.map { seq -> seq.map { it.a }.filter { !it.special } }.toSet()
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
        val actualSequences = v.validSequences.map { seq -> seq.map { ab -> ab.a }.filter { !it.special } }.toSet()
        logger().debug("ACTUAL SEQUENCES: ${str(actualSequences)}")
        logger().debug("UNEXPECTED SEQUENCES: ${str(actualSequences - expectedSequences)}")
        logger().debug("MISSING SEQUENCES: ${str(expectedSequences - actualSequences)}")
        logger().debug("MODEL:\n" + minedModel)
        assertEquals(expectedSequences, actualSequences)
        assertTrue(v.noDeadParts)
        assertTrue(v.isSound)
    }
}