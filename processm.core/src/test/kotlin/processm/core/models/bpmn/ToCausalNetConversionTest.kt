package processm.core.models.bpmn

import processm.core.models.causalnet.Node
import processm.core.models.causalnet.causalnet
import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

class ToCausalNetConversionTest {

    private fun task(n: Int) = Node("Task $n")
    private fun startEvent(n: Int? = null) = Node("Start Event" + (if (n != null) " $n" else ""))
    private fun endEvent(n: Int? = null) = Node("End Event" + (if (n != null) " $n" else ""))

    @Test
    fun a10() {
        val bpmnModel = File("src/test/resources/bpmn-miwg-test-suite/Reference/A.1.0.bpmn").inputStream().use { xml ->
            BPMNModel.fromXML(xml)
        }
        val converted = bpmnModel.toCausalNet()
        println(converted)
        val expected = causalnet {
            start splits startEvent()
            startEvent() splits task(1)
            task(1) splits task(2)
            task(2) splits task(3)
            task(3) splits endEvent()
            endEvent() splits end
            start joins startEvent()
            startEvent() joins task(1)
            task(1) joins task(2)
            task(2) joins task(3)
            task(3) joins endEvent()
            endEvent() joins end
        }
        assertTrue { expected.structurallyEquals(converted) }
    }

    @Test
    fun a20() {
        val splitFlow = Node("Gateway\n(Split Flow)")
        val mergeFlow = Node("Gateway\n(Merge Flows)")
        val expected = causalnet {
            start splits startEvent()
            startEvent() splits task(1)
            task(1) splits splitFlow
            splitFlow splits task(2) or task(3) or task(4)
            task(2) splits endEvent()
            task(3) splits mergeFlow
            task(4) splits mergeFlow
            mergeFlow splits endEvent()
            endEvent() splits end
            start joins startEvent()
            startEvent() joins task(1)
            task(1) joins splitFlow
            splitFlow joins task(2)
            splitFlow joins task(3)
            splitFlow joins task(4)
            task(3) or task(4) join mergeFlow
            task(2) or mergeFlow or task(2) + mergeFlow join endEvent()
            endEvent() joins end
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
            start splits startEvent()
            startEvent() splits task(1)
            task(1) splits splitFlow
            splitFlow splits task(2) or task(3) or task(4)
            task(2) splits endEvent()
            task(3) splits mergeFlow
            task(4) splits mergeFlow
            mergeFlow splits endEvent()
            endEvent() splits end
            start joins startEvent()
            startEvent() joins task(1)
            task(1) joins splitFlow
            splitFlow joins task(2)
            splitFlow joins task(3)
            splitFlow joins task(4)
            task(3) or task(4) join mergeFlow
            task(2) or mergeFlow or task(2) + mergeFlow join endEvent()
            endEvent() joins end
        }
        println(expected)
        val bpmnModel = File("src/test/resources/bpmn-miwg-test-suite/Reference/A.2.1.bpmn").inputStream().use { xml ->
            BPMNModel.fromXML(xml)
        }
        val converted = bpmnModel.toCausalNet()
        assertTrue { expected.structurallyEquals(converted) }
    }

    @Test
    fun a40() {
        val esp1start = Node("Expanded Sub-Process 1", "start")
        val esp1end = Node("Expanded Sub-Process 1", "end")
        val esp2start = Node("Expanded Sub-Process 2", "start")
        val esp2end = Node("Expanded Sub-Process 2", "end")
        val expected = causalnet {
            start splits startEvent(1) + startEvent(2)
            startEvent(1) splits task(1)
            startEvent(2) splits task(3)
            task(1) splits task(2) + task(3)
            task(2) splits endEvent(1)
            endEvent(1) splits end
            task(3) splits esp1start + esp2start
            esp1start splits startEvent(3)
            esp2start splits startEvent(4)
            startEvent(3) splits task(4)
            task(4) splits endEvent(3)
            endEvent(3) splits esp1end
            esp1end splits task(5)
            task(5) splits task(2) + endEvent(2)
            endEvent(2) splits end
            startEvent(4) splits task(6)
            task(6) splits endEvent(4)
            endEvent(4) splits esp2end
            esp2end splits endEvent(5)
            endEvent(5) splits end
            start joins startEvent(1)
            start joins startEvent(2)
            startEvent(1) joins task(1)
            task(1) + task(5) join task(2)
            task(1) + startEvent(2) join task(3)
            task(2) joins endEvent(1)
            task(3) joins esp1start
            esp1start joins startEvent(3)
            startEvent(3) joins task(4)
            task(4) joins endEvent(3)
            endEvent(3) joins esp1end
            esp1end joins task(5)
            task(5) joins endEvent(2)
            task(3) joins esp2start
            esp2start joins startEvent(4)
            startEvent(4) joins task(6)
            task(6) joins endEvent(4)
            endEvent(4) joins esp2end
            esp2end joins endEvent(5)
            endEvent(1) or
                    endEvent(2) or
                    endEvent(5) or
                    endEvent(1) + endEvent(2) or
                    endEvent(1) + endEvent(5) or
                    endEvent(2) + endEvent(5) or
                    endEvent(1) + endEvent(2) + endEvent(5) join end
        }
        val bpmnModel = File("src/test/resources/bpmn-miwg-test-suite/Reference/A.4.0.bpmn").inputStream().use { xml ->
            BPMNModel.fromXML(xml)
        }
        val converted = bpmnModel.toCausalNet()
        assertTrue { expected.structurallyEquals(converted) }
    }

    @Test
    fun `expanded subprocess`() {
        val xml = """<?xml version="1.0" encoding="UTF-8"?>
<bpmn:definitions xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xmlns:bpmn="http://www.omg.org/spec/BPMN/20100524/MODEL" xmlns:bpmndi="http://www.omg.org/spec/BPMN/20100524/DI" xmlns:dc="http://www.omg.org/spec/DD/20100524/DC" xmlns:di="http://www.omg.org/spec/DD/20100524/DI" id="Definitions_1" targetNamespace="http://bpmn.io/schema/bpmn" exporter="bpmn-js (https://demo.bpmn.io)" exporterVersion="6.4.1">
  <bpmn:process id="Process_02t8lp6" isExecutable="false">
    <bpmn:laneSet />
    <bpmn:startEvent id="StartEvent_1sxn4ti" name="Start Event 2">
      <bpmn:outgoing>SequenceFlow_1lj9myu</bpmn:outgoing>
    </bpmn:startEvent>
    <bpmn:subProcess id="SubProcess_007pvm8" name="Expanded Sub-Process 1">
      <bpmn:incoming>Flow_1is88ib</bpmn:incoming>
      <bpmn:outgoing>SequenceFlow_00l4erw</bpmn:outgoing>
      <bpmn:endEvent id="EndEvent_1o0nqza" name="End Event 3">
        <bpmn:incoming>SequenceFlow_112vhms</bpmn:incoming>
      </bpmn:endEvent>
      <bpmn:task id="Task_1kwjzx8" name="Task 4">
        <bpmn:incoming>SequenceFlow_15rozwl</bpmn:incoming>
        <bpmn:outgoing>SequenceFlow_112vhms</bpmn:outgoing>
      </bpmn:task>
      <bpmn:startEvent id="StartEvent_05h78dh" name="Start Event 3">
        <bpmn:outgoing>SequenceFlow_15rozwl</bpmn:outgoing>
      </bpmn:startEvent>
      <bpmn:sequenceFlow id="SequenceFlow_15rozwl" sourceRef="StartEvent_05h78dh" targetRef="Task_1kwjzx8" />
      <bpmn:sequenceFlow id="SequenceFlow_112vhms" sourceRef="Task_1kwjzx8" targetRef="EndEvent_1o0nqza" />
    </bpmn:subProcess>
    <bpmn:endEvent id="EndEvent_08xjrw8" name="End Event 2">
      <bpmn:incoming>SequenceFlow_08byrqq</bpmn:incoming>
    </bpmn:endEvent>
    <bpmn:task id="Task_1jad04q" name="Task 5">
      <bpmn:incoming>SequenceFlow_00l4erw</bpmn:incoming>
      <bpmn:outgoing>SequenceFlow_08byrqq</bpmn:outgoing>
    </bpmn:task>
    <bpmn:task id="Task_1r2li5t" name="Task 3">
      <bpmn:incoming>SequenceFlow_1lj9myu</bpmn:incoming>
      <bpmn:outgoing>Flow_1is88ib</bpmn:outgoing>
    </bpmn:task>
    <bpmn:sequenceFlow id="SequenceFlow_08byrqq" sourceRef="Task_1jad04q" targetRef="EndEvent_08xjrw8" />
    <bpmn:sequenceFlow id="SequenceFlow_00l4erw" sourceRef="SubProcess_007pvm8" targetRef="Task_1jad04q" />
    <bpmn:sequenceFlow id="SequenceFlow_1lj9myu" sourceRef="StartEvent_1sxn4ti" targetRef="Task_1r2li5t" />
    <bpmn:sequenceFlow id="Flow_1is88ib" sourceRef="Task_1r2li5t" targetRef="SubProcess_007pvm8" />
  </bpmn:process>
</bpmn:definitions> 
        """.trimIndent()
        val bpmnModel = xml.byteInputStream().use { BPMNModel.fromXML(it) }
        val actual = bpmnModel.toCausalNet()
        val espstart = Node("Expanded Sub-Process 1", "start")
        val espend = Node("Expanded Sub-Process 1", "end")
        val expected = causalnet {
            start splits startEvent(2)
            startEvent(2) splits task(3)
            task(3) splits espstart
            espstart splits startEvent(3)
            startEvent(3) splits task(4)
            task(4) splits endEvent(3)
            endEvent(3) splits espend
            espend splits task(5)
            task(5) splits endEvent(2)
            endEvent(2) splits end
            start joins startEvent(2)
            startEvent(2) joins task(3)
            task(3) joins espstart
            espstart joins startEvent(3)
            startEvent(3) joins task(4)
            task(4) joins endEvent(3)
            endEvent(3) joins espend
            espend joins task(5)
            task(5) joins endEvent(2)
            endEvent(2) joins end
        }
        assertTrue { actual.structurallyEquals(expected) }
    }
}