package processm.core.models.bpmn

import processm.core.models.causalnet.Node
import processm.core.models.causalnet.causalnet
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ToCausalNetConversionTest {

    val startEvent = Node("Start Event")
    val endEvent = Node("End Event")
    val task1 = Node("Task 1")
    val task2 = Node("Task 2")
    val task3 = Node("Task 3")
    val task4 = Node("Task 4")

    @Test
    fun a10() {
        val bpmnModel = File("src/test/resources/bpmn-miwg-test-suite/Reference/A.1.0.bpmn").inputStream().use { xml ->
            BPMNModel.fromXML(xml)
        }
        val converted = bpmnModel.toCausalNet()
        println(converted)
        val expected = causalnet {
            start splits startEvent
            startEvent splits task1
            task1 splits task2
            task2 splits task3
            task3 splits endEvent
            endEvent splits end
            start joins startEvent
            startEvent joins task1
            task1 joins task2
            task2 joins task3
            task3 joins endEvent
            endEvent joins end
        }
        assertTrue { expected.structurallyEquals(converted) }
    }

    @Test
    fun a20() {
        val splitFlow = Node("Gateway\n(Split Flow)")
        val mergeFlow = Node("Gateway\n(Merge Flows)")
        val expected = causalnet {
            start splits startEvent
            startEvent splits task1
            task1 splits splitFlow
            splitFlow splits task2 or task3 or task4
            task2 splits endEvent
            task3 splits mergeFlow
            task4 splits mergeFlow
            mergeFlow splits endEvent
            endEvent splits end
            start joins startEvent
            startEvent joins task1
            task1 joins splitFlow
            splitFlow joins task2
            splitFlow joins task3
            splitFlow joins task4
            task3 or task4 join mergeFlow
            task2 or mergeFlow or task2+mergeFlow join endEvent
            endEvent joins end
        }
        println(expected)
        val bpmnModel = File("src/test/resources/bpmn-miwg-test-suite/Reference/A.2.0.bpmn").inputStream().use { xml ->
            BPMNModel.fromXML(xml)
        }
        val converted = bpmnModel.toCausalNet()
        assertTrue { expected.structurallyEquals(converted) }
    }

    @Test
    fun a21() {
        val splitFlow = Node("Gateway\n(Split Flow)")
        val mergeFlow = Node("Gateway\n(Merge Flows)")
        val expected = causalnet {
            start splits startEvent
            startEvent splits task1
            task1 splits splitFlow
            splitFlow splits task2 or task3 or task4
            task2 splits endEvent
            task3 splits mergeFlow
            task4 splits mergeFlow
            mergeFlow splits endEvent
            endEvent splits end
            start joins startEvent
            startEvent joins task1
            task1 joins splitFlow
            splitFlow joins task2
            splitFlow joins task3
            splitFlow joins task4
            task3 or task4 join mergeFlow
            task2 or mergeFlow or task2+mergeFlow join endEvent
            endEvent joins end
        }
        println(expected)
        val bpmnModel = File("src/test/resources/bpmn-miwg-test-suite/Reference/A.2.1.bpmn").inputStream().use { xml ->
            BPMNModel.fromXML(xml)
        }
        val converted = bpmnModel.toCausalNet()
        assertTrue { expected.structurallyEquals(converted) }
    }
}