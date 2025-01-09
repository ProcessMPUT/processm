package processm

import processm.conformance.models.alignments.CompositeAligner
import processm.core.log.XMLXESInputStream
import processm.core.log.hierarchical.HoneyBadgerHierarchicalXESInputStream
import processm.core.log.hierarchical.InMemoryXESProcessing
import processm.core.models.petrinet.converters.toPetriNet
import processm.miners.processtree.inductiveminer.OnlineInductiveMiner
import kotlin.test.Test
import kotlin.test.assertEquals

class RepairExampleTest {

    @OptIn(InMemoryXESProcessing::class)
    @Test
    fun `computing alignments on a PetriNet from repairExample`() {
        val stream = this::class.java.classLoader.getResourceAsStream("repairExample.xes")!!.use {
            HoneyBadgerHierarchicalXESInputStream(XMLXESInputStream(it)).apply {
                first()
            }
        }
        val model = OnlineInductiveMiner().processLog(stream).toPetriNet()
        val alignments = CompositeAligner(model).align(stream.first())
        assertEquals(stream.first().traces.count(), alignments.count())
    }
}