package processm.core.models.bpmn.converters

import processm.core.models.bpmn.BPMNModel
import processm.core.models.causalnet.MutableCausalNet
import processm.core.models.causalnet.Node
import processm.core.models.causalnet.causalnet
import processm.core.verifiers.causalnet.CausalNetVerifierImpl
import java.io.File
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class BPMNModel2CausalNetTest {

    private fun task(n: Int) = Node("Task $n")
    private fun startEvent(n: Int? = null) = Node("Start Event" + (if (n != null) " $n" else ""))
    private fun endEvent(n: Int? = null) = Node("End Event" + (if (n != null) " $n" else ""))

    @Test
    fun a10() {
        val bpmnModel =
            File("src/test/resources/bpmn-miwg-test-suite/Reference/A.1.0.bpmn").absoluteFile.inputStream().use { xml ->
                BPMNModel.fromXML(xml)
            }
        val converted = bpmnModel.toCausalNet()
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
        assertTrue { CausalNetVerifierImpl(converted).isStructurallySound }
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
        val bpmnModel = File("src/test/resources/bpmn-miwg-test-suite/Reference/A.2.0.bpmn").inputStream().use { xml ->
            BPMNModel.fromXML(xml)
        }
        val converted = bpmnModel.toCausalNet()
        assertTrue { CausalNetVerifierImpl(converted).isStructurallySound }
        assertTrue { expected.structurallyEquals(converted) }
    }

    @Test
    fun a21() {
        val splitFlow = Node("Gateway\r\n(Split Flow)")
        val mergeFlow = Node("Gateway\n(Merge Flows)")
        val expected = causalnet {
            start splits startEvent()
            startEvent() splits task(1)
            task(1) splits splitFlow
            splitFlow splits task(2) or task(3) or task(4)
            task(2) splits task(3) or endEvent()
            task(3) splits mergeFlow
            task(4) splits task(3) or mergeFlow
            mergeFlow splits endEvent()
            endEvent() splits end
            start joins startEvent()
            startEvent() joins task(1)
            task(1) joins splitFlow
            splitFlow joins task(2)
            splitFlow or task(2) or task(4) join task(3)
            splitFlow joins task(4)
            task(3) or task(4) join mergeFlow
            task(2) or mergeFlow or task(2) + mergeFlow join endEvent()
            endEvent() joins end
        }
        val bpmnModel = File("src/test/resources/bpmn-miwg-test-suite/Reference/A.2.1.bpmn").inputStream().use { xml ->
            BPMNModel.fromXML(xml)
        }
        val converted = bpmnModel.toCausalNet()
        assertTrue { CausalNetVerifierImpl(converted).isStructurallySound }
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
        assertTrue { CausalNetVerifierImpl(converted).isStructurallySound }
        assertTrue { expected.structurallyEquals(converted) }
    }

    @Test
    fun a41() {
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
        val bpmnModel = File("src/test/resources/bpmn-miwg-test-suite/Reference/A.4.1.bpmn").inputStream().use { xml ->
            BPMNModel.fromXML(xml)
        }
        // Apparently, names in A.4.1 contain trailing spaces. The sole purpose of the code below is to rewrite the obtained cnet renaming nodes
        val tmp = bpmnModel.toCausalNet()
        val converted = MutableCausalNet()
        converted.copyFrom(tmp) { node -> Node(node.activity.trim(), node.instanceId.trim(), node.isArtificial) }

        assertTrue { CausalNetVerifierImpl(converted).isStructurallySound }
        assertTrue { expected.structurallyEquals(converted) }
    }

    @Test
    fun `b10`() {
        val bpmnModel = File("src/test/resources/bpmn-miwg-test-suite/Reference/B.1.0.bpmn").inputStream().use { xml ->
            BPMNModel.fromXML(xml)
        }
        // B.1.0.bpmn contains TCallActivity, which is not supported in the current state of the converter
        assertFailsWith<UnsupportedOperationException> { bpmnModel.toCausalNet() }
    }

    @Test
    fun `c10`() {
        val expected = causalnet {
            Node("ID:sid-F0D29912-929D-491C-8D23-73BD80CF980A") joins Node("7 days")
            Node("7 days") splits Node("ID:sid-BC9AC0B6-1785-4E35-A974-7FEF1A586B9D")
            Node("Review\u000asuccessful?") or Node("Assign\u000aApprover") join Node("Approve Invoice")
            Node("Approve Invoice") splits Node("Invoice\u000aapproved?")
            Node("Archive\u000aoriginal") + Node("Assign\u000aApprover") join Node("Approver to \u000abe assigned")
            Node("Approver to \u000abe assigned") splits Node("Assign approver")
            Node("Prepare\u000d\u000aBank\u000d\u000aTransfer") joins Node("Archive\u000aInvoice")
            Node("Archive\u000aInvoice") splits Node("Invoice\u000aprocessed")
            Node("Scan Invoice") joins Node("Archive\u000aoriginal")
            Node("Archive\u000aoriginal") splits Node("Approver to \u000abe assigned")
            Node("Invoice\u000areceived", "NAME:BPMN MIWG Test Case C.1.0", false) + Node("Assign approver") join Node("Assign\u000aApprover")
            Node("Assign\u000aApprover") splits Node("Approve Invoice") + Node("Approver to \u000abe assigned")
            Node("Approver to \u000abe assigned") joins Node("Assign approver")
            Node("Assign approver") splits Node("ID:sid-F0D29912-929D-491C-8D23-73BD80CF980A") + Node("Assign\u000aApprover")
            Node("Review and document result") joins Node("ID:sid-282524E6-660F-431D-8F19-1C3E9E9DE817")
            Node("ID:sid-282524E6-660F-431D-8F19-1C3E9E9DE817") splits end
            Node("7 days") joins Node("ID:sid-BC9AC0B6-1785-4E35-A974-7FEF1A586B9D")
            Node("ID:sid-BC9AC0B6-1785-4E35-A974-7FEF1A586B9D") splits end
            Node("Approve Invoice") joins Node("Invoice\u000aapproved?")
            Node("Invoice\u000aapproved?") splits Node("Rechnung kl\u00e4ren") or Node("Prepare\u000d\u000aBank\u000d\u000aTransfer")
            Node("Archive\u000aInvoice") joins Node("Invoice\u000aprocessed")
            Node("Invoice\u000aprocessed") splits end
            start joins Node("Invoice\u000areceived", "NAME:Team-Assistant", false)
            Node("Invoice\u000areceived", "NAME:Team-Assistant", false) splits Node("Scan Invoice")
            start + Node("Scan Invoice") join Node("Invoice\u000areceived", "NAME:BPMN MIWG Test Case C.1.0", false)
            Node("Invoice\u000areceived", "NAME:BPMN MIWG Test Case C.1.0", false) splits Node("Assign\u000aApprover")
            Node("Review\u000asuccessful?") joins Node("Invoice not\u000aprocessed")
            Node("Invoice not\u000aprocessed") splits end
            Node("ID:sid-F0D29912-929D-491C-8D23-73BD80CF980A") + Node("Rechnung kl\u00e4ren") join Node("Invoice review\u000a needed")
            Node("Invoice review\u000a needed") splits Node("Review and document result")
            Node("Invoice\u000aapproved?") joins Node("Prepare\u000d\u000aBank\u000d\u000aTransfer")
            Node("Prepare\u000d\u000aBank\u000d\u000aTransfer") splits Node("Archive\u000aInvoice")
            Node("Invoice\u000aapproved?") + Node("Review and document result") join Node("Rechnung kl\u00e4ren")
            Node("Rechnung kl\u00e4ren") splits Node("Review\u000asuccessful?") + Node("Invoice review\u000a needed")
            Node("Rechnung kl\u00e4ren") joins Node("Review\u000asuccessful?")
            Node("Review\u000asuccessful?") splits Node("Approve Invoice") or Node("Invoice not\u000aprocessed")
            Node("Invoice review\u000a needed") joins Node("Review and document result")
            Node("Review and document result") splits Node("ID:sid-282524E6-660F-431D-8F19-1C3E9E9DE817") + Node("Rechnung kl\u00e4ren")
            Node("Invoice\u000areceived", "NAME:Team-Assistant", false) joins Node("Scan Invoice")
            Node("Scan Invoice") splits Node("Archive\u000aoriginal") + Node("Invoice\u000areceived", "NAME:BPMN MIWG Test Case C.1.0", false)
            Node("Invoice\u000aprocessed") + Node("Invoice not\u000aprocessed") + Node("ID:sid-BC9AC0B6-1785-4E35-A974-7FEF1A586B9D") or Node("Invoice\u000aprocessed") + Node("Invoice not\u000aprocessed") or Node("Invoice not\u000aprocessed") + Node("ID:sid-BC9AC0B6-1785-4E35-A974-7FEF1A586B9D") or Node("ID:sid-282524E6-660F-431D-8F19-1C3E9E9DE817") + Node("Invoice not\u000aprocessed") or Node("Invoice\u000aprocessed") + Node("ID:sid-BC9AC0B6-1785-4E35-A974-7FEF1A586B9D") or Node("Invoice\u000aprocessed") or Node("Invoice not\u000aprocessed") or Node("Invoice\u000aprocessed") + Node("ID:sid-282524E6-660F-431D-8F19-1C3E9E9DE817") or Node("Invoice\u000aprocessed") + Node("ID:sid-282524E6-660F-431D-8F19-1C3E9E9DE817") + Node("ID:sid-BC9AC0B6-1785-4E35-A974-7FEF1A586B9D") or Node("ID:sid-282524E6-660F-431D-8F19-1C3E9E9DE817") + Node("ID:sid-BC9AC0B6-1785-4E35-A974-7FEF1A586B9D") or Node("Invoice\u000aprocessed") + Node("ID:sid-282524E6-660F-431D-8F19-1C3E9E9DE817") + Node("Invoice not\u000aprocessed") or Node("ID:sid-282524E6-660F-431D-8F19-1C3E9E9DE817") + Node("Invoice not\u000aprocessed") + Node("ID:sid-BC9AC0B6-1785-4E35-A974-7FEF1A586B9D") or Node("ID:sid-282524E6-660F-431D-8F19-1C3E9E9DE817") or Node("ID:sid-BC9AC0B6-1785-4E35-A974-7FEF1A586B9D") or Node("Invoice\u000aprocessed") + Node("ID:sid-282524E6-660F-431D-8F19-1C3E9E9DE817") + Node("Invoice not\u000aprocessed") + Node("ID:sid-BC9AC0B6-1785-4E35-A974-7FEF1A586B9D") join end
            Node("Assign approver") joins Node("ID:sid-F0D29912-929D-491C-8D23-73BD80CF980A")
            Node("ID:sid-F0D29912-929D-491C-8D23-73BD80CF980A") splits Node("Invoice review\u000a needed") or Node("7 days")
            start splits Node("Invoice\u000areceived", "NAME:Team-Assistant", false) + Node("Invoice\u000areceived", "NAME:BPMN MIWG Test Case C.1.0", false)
        }
        val bpmnModel = File("src/test/resources/bpmn-miwg-test-suite/Reference/C.1.0.bpmn").inputStream().use { xml ->
            BPMNModel.fromXML(xml)
        }
        val converted = bpmnModel.toCausalNet()
        assertTrue { CausalNetVerifierImpl(converted).isStructurallySound }
        assertTrue { expected.structurallyEquals(converted) }
    }

    @Test
    fun `c11`() {
        val expected = causalnet {
            Node("Review\u000asuccessful?") or Node("Assign\u000d\u000aApprover") join Node("Approve Invoice")
            Node("Approve Invoice") splits Node("Invoice\u000d\u000aapproved?")
            Node("Prepare\u000d\u000aBank\u000d\u000aTransfer") joins Node("Archive\u000aInvoice")
            Node("Archive\u000aInvoice") splits Node("Invoice\u000aprocessed")
            Node("Invoice\u000d\u000areceived") joins Node("Assign\u000d\u000aApprover")
            Node("Assign\u000d\u000aApprover") splits Node("Approve Invoice")
            Node("Archive\u000aInvoice") joins Node("Invoice\u000aprocessed")
            Node("Invoice\u000aprocessed") splits end
            Node("Approve Invoice") joins Node("Invoice\u000d\u000aapproved?")
            Node("Invoice\u000d\u000aapproved?") splits Node("Rechnung kl\u00e4ren") or Node("Prepare\u000d\u000aBank\u000d\u000aTransfer")
            start joins Node("Invoice\u000d\u000areceived")
            Node("Invoice\u000d\u000areceived") splits Node("Assign\u000d\u000aApprover")
            Node("Review\u000asuccessful?") joins Node("Invoice not\u000aprocessed")
            Node("Invoice not\u000aprocessed") splits end
            Node("Invoice\u000d\u000aapproved?") joins Node("Prepare\u000d\u000aBank\u000d\u000aTransfer")
            Node("Prepare\u000d\u000aBank\u000d\u000aTransfer") splits Node("Archive\u000aInvoice")
            Node("Invoice\u000d\u000aapproved?") joins Node("Rechnung kl\u00e4ren")
            Node("Rechnung kl\u00e4ren") splits Node("Review\u000asuccessful?")
            Node("Rechnung kl\u00e4ren") joins Node("Review\u000asuccessful?")
            Node("Review\u000asuccessful?") splits Node("Approve Invoice") or Node("Invoice not\u000aprocessed")
            Node("Invoice not\u000aprocessed") + Node("Invoice\u000aprocessed") or Node("Invoice\u000aprocessed") or Node("Invoice not\u000aprocessed") join end
            start splits Node("Invoice\u000d\u000areceived")
        }
        val bpmnModel = File("src/test/resources/bpmn-miwg-test-suite/Reference/C.1.1.bpmn").inputStream().use { xml ->
            BPMNModel.fromXML(xml)
        }
        val converted = bpmnModel.toCausalNet()
        assertTrue { CausalNetVerifierImpl(converted).isStructurallySound }
        assertTrue { expected.structurallyEquals(converted) }
    }

    @Test
    fun `c20`() {
       val bpmnModel = File("src/test/resources/bpmn-miwg-test-suite/Reference/C.2.0.bpmn").inputStream().use { xml ->
           BPMNModel.fromXML(xml)
        }
        val converted = bpmnModel.toCausalNet()
        assertTrue { CausalNetVerifierImpl(converted).isStructurallySound }
    }

    @Test
    fun `c30`() {
       val bpmnModel = File("src/test/resources/bpmn-miwg-test-suite/Reference/C.3.0.bpmn").inputStream().use { xml ->
           BPMNModel.fromXML(xml)
        }
        val converted = bpmnModel.toCausalNet()
        assertTrue { CausalNetVerifierImpl(converted).isStructurallySound }
    }

    @Test
    fun `c40`() {
        val bpmnModel = File("src/test/resources/bpmn-miwg-test-suite/Reference/C.4.0.bpmn").inputStream().use { xml ->
            BPMNModel.fromXML(xml)
        }
        val converted = bpmnModel.toCausalNet()
        assertTrue { CausalNetVerifierImpl(converted).isStructurallySound }
    }

    @Test
    fun `c50`() {
        val bpmnModel = File("src/test/resources/bpmn-miwg-test-suite/Reference/C.5.0.bpmn").inputStream().use { xml ->
            BPMNModel.fromXML(xml)
        }
        // C.5.0.bpmn contains TCallActivity, which is not supported in the current state of the converter
        assertFailsWith<UnsupportedOperationException> { bpmnModel.toCausalNet() }
    }

    @Test
    fun `c60`() {
        val bpmnModel = File("src/test/resources/bpmn-miwg-test-suite/Reference/C.6.0.bpmn").inputStream().use { xml ->
            BPMNModel.fromXML(xml)
        }
        // C.6.0.bpmn contains compensations, which are not supported in the current state of the converter.
        assertFailsWith<UnsupportedOperationException> { bpmnModel.toCausalNet() }
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
