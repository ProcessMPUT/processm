package processm

import processm.conformance.models.alignments.AStar
import processm.core.DBTestHelper
import processm.core.log.XMLXESInputStream
import processm.core.log.hierarchical.DBHierarchicalXESInputStream
import processm.core.log.hierarchical.HoneyBadgerHierarchicalXESInputStream
import processm.core.log.hierarchical.InMemoryXESProcessing
import processm.core.models.petrinet.DBSerializer
import processm.core.models.petrinet.converters.toPetriNet
import processm.core.persistence.connection.DBCache
import processm.core.querylanguage.Query
import processm.enhancement.kpi.Calculator
import processm.miners.processtree.inductiveminer.OnlineInductiveMiner
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RepairExampleTest {
    @OptIn(InMemoryXESProcessing::class)
    @Test
    fun `computing alignments on a PetriNet from repairExample`() {
        val stream =
            RepairExampleTest::class.java.classLoader.getResourceAsStream("repairExample.xes")!!.use { inp ->
                HoneyBadgerHierarchicalXESInputStream(XMLXESInputStream(inp)).also { it.first() }
            }
        val model = OnlineInductiveMiner().processLog(stream).toPetriNet()
        val report = Calculator(model).calculate(stream)
        assertEquals(report.alignments.size, stream.first().traces.count())
        assertTrue { report.logKPI.isNotEmpty() }
        assertTrue { report.traceKPI.isNotEmpty() }
    }

    @Test
    fun `computing alignments on a PetriNet from repairExample from the DB`() {
        RepairExampleTest::class.java.classLoader.getResourceAsStream("repairExample.xes")!!.use { inp ->
            DBTestHelper.loadLog(inp)
        }
        val stream = DBHierarchicalXESInputStream(
            DBTestHelper.dbName,
            Query("where l:name='repairExample.mxml'")
        )
        val model = OnlineInductiveMiner().processLog(stream).toPetriNet()
        val modelId = DBSerializer.insert(DBCache.get(DBTestHelper.dbName).database, model)
        val deserializedModel = DBSerializer.fetch(DBCache.get(DBTestHelper.dbName).database, modelId)
        val report = Calculator(deserializedModel).calculate(stream)
        assertEquals(report.alignments.size, stream.first().traces.count())
        assertTrue { report.logKPI.isNotEmpty() }
        assertTrue { report.traceKPI.isNotEmpty() }
    }

    @OptIn(InMemoryXESProcessing::class)
    @Test
    fun `computing alignments on a PetriNet from repairExample - occasional StackOverflow in AStar`() {
        val stream =
            RepairExampleTest::class.java.classLoader.getResourceAsStream("repairExample.xes")!!.use { inp ->
                HoneyBadgerHierarchicalXESInputStream(XMLXESInputStream(inp)).also { it.first() }
            }
        repeat(100) {
            val model = OnlineInductiveMiner().processLog(stream).toPetriNet()
            val alignments = AStar(model).align(stream.first())
            assertEquals(stream.first().traces.count(), alignments.count())
        }
    }
}