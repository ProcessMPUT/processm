package processm.core.models.bpmn

import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestFactory
import processm.helpers.mapToSet
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class BPMNModelTest {

    val a40export =
        BPMNModel.fromXML(javaClass.getResourceAsStream("/bpmn-miwg-test-suite/bpmn.io (Cawemo, Camunda Modeler) 1.12.0/A.4.0-export.bpmn")!!)
    val c10export =
        BPMNModel.fromXML(javaClass.getResourceAsStream("/bpmn-miwg-test-suite/bpmn.io (Cawemo, Camunda Modeler) 1.12.0/C.1.0-export.bpmn")!!)

    @Test
    fun `bpmnio A40-export activities`() {
        assertEquals(
            setOf(
                "Task 1", "Task 2", "Task 3", "Task 4", "Task 5", "Task 6",
                "Expanded Sub-Process 1", "Expanded Sub-Process 2",
                "Start Event 1", "Start Event 2", "Start Event 3", "Start Event 4",
                "End Event 1", "End Event 2", "End Event 3", "End Event 4", "End Event 5"
            ),
            a40export.activities.mapToSet { it.name }
        )
    }

    @Test
    fun `bpmnio A40-export start`() {
        assertEquals(
            setOf("Start Event 1", "Start Event 2", "Expanded Sub-Process 1"),
            a40export.startActivities.mapToSet { it.name }
        )
    }

    @Test
    fun `bpmnio A40-export end`() {
        assertEquals(
            setOf("End Event 1", "End Event 2", "End Event 5"),
            a40export.endActivities.mapToSet { it.name }
        )
    }

    @Test
    fun `bpmnio C10-export gateways`() {
        val dps = c10export.decisionPoints.filter { it.isRealDecision }
        assertEquals(3, dps.count())
        assertTrue { dps.all { it.possibleOutcomes.size == 2 } }
    }

    @Test
    fun `bpmnio A20-export gateways`() {
        val dps =
            BPMNModel.fromXML(javaClass.getResourceAsStream("/bpmn-miwg-test-suite/bpmn.io (Cawemo, Camunda Modeler) 1.12.0/A.2.0-export.bpmn")!!).decisionPoints
        assertEquals(8, dps.count())
        assertEquals(3, dps.filter { it.isRealDecision }.single().possibleOutcomes.size)
    }

    @Test
    fun `bpmnio A21-export gateways`() {
        val dps =
            BPMNModel.fromXML(javaClass.getResourceAsStream("/bpmn-miwg-test-suite/bpmn.io (Cawemo, Camunda Modeler) 1.12.0/A.2.1-export.bpmn")!!).decisionPoints
        assertEquals(8, dps.count())
        assertEquals(3, dps.filter { it.isRealDecision }.single().possibleOutcomes.size)
    }

    @Test
    fun `bpmnio C10-export start`() {
        assertEquals(2, c10export.startActivities.count())
    }

    @Test
    fun `bpmnio C10-export end`() {
        assertEquals(4, c10export.endActivities.count())
    }

    @Test
    fun `model instances are not supported`() {
        assertFailsWith<UnsupportedOperationException> { a40export.createInstance() }
    }

    @TestFactory
    fun `test for exceptions`(): Iterator<DynamicTest> =
        (File("src/test/resources/bpmn-miwg-test-suite/bpmn.io (Cawemo, Camunda Modeler) 1.12.0/").walk() +
                File("src/test/resources/bpmn-miwg-test-suite/Reference/").walk())
            .filter { it.extension.lowercase() == "bpmn" }
            .sortedBy { it.name }
            .iterator()
            .asSequence()
            .map { file ->
                DynamicTest.dynamicTest(file.name, file.toURI()) {
                    val p = BPMNModel.fromXML(file.inputStream())
                    p.activities.toList()   //read activities
                    p.startActivities.toList()
                    p.endActivities.toList()
                    p.decisionPoints.toList()   //read decision points
                }
            }
            .iterator()

}
