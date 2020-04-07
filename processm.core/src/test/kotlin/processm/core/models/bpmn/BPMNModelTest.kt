package processm.core.models.bpmn

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class BPMNModelTest {

    val a40export =
        BPMNModel.fromXML(File("src/test/resources/bpmn-miwg-test-suite/bpmn.io (Cawemo, Camunda Modeler) 1.12.0/A.4.0-export.bpmn").inputStream())
    val c10export =
        BPMNModel.fromXML(File("src/test/resources/bpmn-miwg-test-suite/bpmn.io (Cawemo, Camunda Modeler) 1.12.0/C.1.0-export.bpmn").inputStream())

    @Test
    fun `bpmnio A40-export activities`() {
        assertEquals(
            setOf(
                "Task 1", "Task 2", "Task 3", "Task 4", "Task 5", "Task 6",
                "Expanded Sub-Process 1", "Expanded Sub-Process 2",
                "Start Event 1", "Start Event 2", "Start Event 3", "Start Event 4",
                "End Event 1", "End Event 2", "End Event 3", "End Event 4", "End Event 5"
            ),
            a40export.activities.map { it.name }.toSet()
        )
    }

    @Test
    fun `bpmnio A40-export start`() {
        assertEquals(
            setOf("Start Event 1", "Start Event 2", "Expanded Sub-Process 1"),
            a40export.startActivities.map { it.name }.toSet()
        )
    }

    @Test
    fun `bpmnio A40-export end`() {
        assertEquals(
            setOf("End Event 1", "End Event 2", "End Event 5"),
            a40export.endActivities.map { it.name }.toSet()
        )
    }

    @Test
    fun `bpmnio C10-export gateways`() {
        assertEquals(3, c10export.decisionPoints.count())
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
}
