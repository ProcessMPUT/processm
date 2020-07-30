package processm.core.models.bpmn

import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.TestFactory
import processm.core.models.bpmn.jaxb.TPerformer
import processm.core.models.bpmn.jaxb.TProcess
import processm.core.models.bpmn.jaxb.TUserTask
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import kotlin.test.Ignore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BPMNXMLServiceTest {
    private val nonStrict = setOf(
        "/ModelFoundry 1.1.1/C.1.1-roundtrip.bpmn",
        "/ModelFoundry 1.1.1/A.2.1-roundtrip.bpmn",
        "/ModelFoundry 1.1.1/C.3.0-roundtrip.bpmn",
        "/ModelFoundry 1.1.1/C.1.0-roundtrip.bpmn",
        "/iGrafx FlowCharter 2013 15.1.1.1580/B.2.0-export.bpmn",
        "/iGrafx Process 2013 for Six Sigma 15.0.4.1565/B.2.0-export.bpmn"
    )

    /**
     * Bpmn.io displays warnings while loading these files, as documented in
     * https://git.processtom.com/processm-team/processm/merge_requests/52
     */
    private val invalidFiles = setOf(
        "/BPMN+ Composer V.10.4/B.2.0-export.bpmn",
        "/W4 BPMN+ Composer V.9.4/B.2.0-export.bpmn",
        "/Aeneis 5.7.89.2400/A.4.1-roundtrip.bpmn",
        "/MID Innovator 12.3.1.20212/C.1.1-export.bpmn",
        "/MID Innovator 12.3.1.20212/C.1.1-roundtrip.bpmn",
        "/Signavio Process Editor 10.0.0/B.2.0-roundtrip.bpmn",
        "/ModelFoundry 1.1.1/B.2.0-roundtrip.bpmn",
        "/ARIS Architect 9.8.3/Signavio 9.7.0/B.2.0-export-rountrip.bpmn"
    )

    /**
     * These files behave strangely and this should be further investigated, see issue #59
     *
     * @see suspiciousXML
     */
    private val problematicFiles = setOf(
        "/BPMN+ Composer V.10.4/C.4.0-export.bpmn",
        "/Aeneis 5.7.89.2400/C.1.0-roundtrip.bpmn",
        "/bpmn.io (Cawemo, Camunda Modeler) 1.12.0/A.1.2-roundtrip.bpmn",
        "/ADONIS NP 8.0/C.3.0-roundtrip.bpmn"   //StAX creates TExpression from <timeDuration xsi:type="tFormalExpression"><![CDATA[PT2H]]></timeDuration> and JAXB creates TFormalExpression
    )

    /**
     * These files behave strangely - their deserialization, serialization and deserialization again does not lead to the same results.
     * They mostly fail in comparison with something being null, while the other thing is not null
     */
    private val nonIdempotent = invalidFiles + problematicFiles

    private val base = "src/test/resources/bpmn-miwg-test-suite"
    private val files = File(base)
        .walk()
        .filter { it.extension.toLowerCase() == "bpmn" }
        .iterator()
        .asSequence()
    private val strictFiles = files.filterNot { nonStrict.any { p -> it.path.endsWith(p) } }
    private val nonStrictFiles = files.filter { nonStrict.any { p -> it.path.endsWith(p) } }
    private val idempotentFiles = strictFiles.filter { !nonIdempotent.any { p -> it.path.endsWith(p) } }
    private val nonIdempotentFiles = strictFiles.filter { nonIdempotent.any { p -> it.path.endsWith(p) } }

    @Tag("BPMN")
    @TestFactory
    fun loadNonStrictWithStAX(): Iterable<DynamicTest> {
        return nonStrictFiles
            .map {
                DynamicTest.dynamicTest(it.path.replace(base, "")) {
                    val (_: Any?, warnings) = BPMNXMLService.load(it.inputStream())
                    assertTrue { warnings.isNotEmpty() }
                }
            }
            .toList()
    }

    @Tag("BPMN")
    @TestFactory
    fun `load non idempotent with StAX`(): Iterable<DynamicTest> {
        return nonIdempotentFiles
            .map {
                DynamicTest.dynamicTest(it.path.replace(base, ""))
                {
                    val (_: Any?, warnings) = BPMNXMLService.load(it.inputStream())
                    assertTrue { warnings.isEmpty() }
                }
            }.toList()
    }

    @Tag("BPMN")
    @TestFactory
    fun `load idempotent with StAX and compare with JAXB`(): Iterable<DynamicTest> {
        return idempotentFiles
            .map {
                DynamicTest.dynamicTest(it.path.replace(base, ""))
                {
                    val (stax, warnings) = BPMNXMLService.load(it.inputStream())
                    val jaxb = BPMNXMLService.loadStrict(it.inputStream())
                    assertTrue { warnings.isEmpty() }
                    assertTrue { JaxbRecursiveComparer(true)(stax, jaxb) }
                }
            }.toList()
    }

    @Tag("BPMN")
    @TestFactory
    fun loadStrictWithJAXB(): Iterable<DynamicTest> {
        return strictFiles
            .map {
                DynamicTest.dynamicTest(it.path.replace(base, ""))
                { BPMNXMLService.loadStrict(it.inputStream()) }
            }.toList()
    }

    @Tag("BPMN")
    @TestFactory
    fun loadAndSave(): Iterable<DynamicTest> {
        return idempotentFiles
            .map {
                DynamicTest.dynamicTest(it.path.replace(base, "")) {
                    val out = ByteArrayOutputStream()
                    val a = BPMNXMLService.loadStrict(it.inputStream())
                    BPMNXMLService.save(a, out)
                    val b = BPMNXMLService.loadStrict(ByteArrayInputStream(out.toByteArray()))
                    /*
                    I have no better how to perform any sort of verification:
                    * XMLUnit is too picky
                    * Equals generated by Equals or Simple Equals plugins of jaxb2 basic on unmarshalled objects frequently yields StackOverflowError
                    * In general, XMLs are not stable, e.g., because they serialize Doubles to string
                     */
                    assertTrue { JaxbRecursiveComparer()(a, b) }
                }
            }.toList()
    }

    @Test
    @Ignore("To be investigated, issue #59")
    fun suspiciousXML() {
        val a = BPMNXMLService.loadStrict(File("src/test/resources/suspicious1.xml").inputStream())
        assertEquals(
            "_404cd32e-8789-46c5-ad72-cdedb860665d",
            (((a.rootElement[1].value as TProcess).flowElement[0].value as TUserTask).resourceRole[0].value as TPerformer).resourceRef.localPart
        )
        val out = ByteArrayOutputStream()
        BPMNXMLService.save(a, out)
        val b = BPMNXMLService.loadStrict(ByteArrayInputStream(out.toByteArray()))
        assertEquals(
            "_404cd32e-8789-46c5-ad72-cdedb860665d",
            (((b.rootElement[1].value as TProcess).flowElement[0].value as TUserTask).resourceRole[0].value as TPerformer).resourceRef.localPart
        )
    }
}