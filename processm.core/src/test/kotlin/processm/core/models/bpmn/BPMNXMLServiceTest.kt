package processm.core.models.bpmn

import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestFactory
import processm.core.models.bpmn.jaxb.TDefinitions
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.lang.reflect.Field
import javax.xml.bind.JAXBElement
import javax.xml.bind.annotation.XmlAttribute
import javax.xml.bind.annotation.XmlElement
import javax.xml.stream.XMLStreamException
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class BPMNXMLServiceTest {
    private val invalidEncoding = setOf(
        "/GenMyModel 0.47/C.1.1-roundtrip.bpmn",
        "/GenMyModel 0.47/C.1.1-export.bpmn",
        "/GenMyModel 0.47/C.1.0-export.bpmn",
        "/GenMyModel 0.47/C.1.0-roundtrip.bpmn",
        "/ARIS Architect 10.0.10/C.1.1-roundtrip.bpmn",
        "/ARIS Architect 10.0.10/C.1.1-export.bpmn",
        "/ARIS Architect 10.0.10/C.1.0-export.bpmn",
        "/ARIS Architect 10.0.10/C.1.0-roundtrip.bpmn"
    )

    private val nonStrict = setOf(
        "/ModelFoundry 1.1.1/C.1.1-roundtrip.bpmn",
        "/ModelFoundry 1.1.1/A.2.1-roundtrip.bpmn",
        "/ModelFoundry 1.1.1/C.3.0-roundtrip.bpmn",
        "/ModelFoundry 1.1.1/C.1.0-roundtrip.bpmn",
        "/iGrafx FlowCharter 2013 15.1.1.1580/B.2.0-export.bpmn",
        "/iGrafx Process 2013 for Six Sigma 15.0.4.1565/B.2.0-export.bpmn"
    )
    private val base = "src/test/resources/bpmn-miwg-test-suite"
    private val files = File(base)
        .walk()
        .filter { it.extension.toLowerCase() == "bpmn" }
        .iterator()
        .asSequence()
    private val strictFiles = files.filter { !(nonStrict + invalidEncoding).any { p -> it.path.endsWith(p) } }

    @TestFactory
    fun load(): Iterable<DynamicTest> {
        return files
            .filter { !invalidEncoding.any { p -> it.path.endsWith(p) } }
            .map { DynamicTest.dynamicTest(it.path.replace(base, "")) { BPMNXMLService.load(it.inputStream()) } }
            .toList()
    }

    @TestFactory
    fun loadInvalidEncoding(): Iterable<DynamicTest> {
        return files
            .filter { invalidEncoding.any { p -> it.path.endsWith(p) } }
            .map {
                DynamicTest.dynamicTest(it.path.replace(base, ""))
                { assertFailsWith<XMLStreamException> { BPMNXMLService.load(it.inputStream()) } }
            }.toList()
    }

    @TestFactory
    fun loadStrict(): Iterable<DynamicTest> {
        return strictFiles
            .map {
                DynamicTest.dynamicTest(it.path.replace(base, ""))
                { BPMNXMLService.loadStrict(it.inputStream()) }
            }.toList()
    }

    private class JaxbRecursiveComparer {

        private val seen = HashSet<Pair<Any, Any>>()

        private fun processAnn(left: Any, right: Any, ann: String): Boolean {
            val getters = left.javaClass.declaredMethods
                .filter { m -> m.canAccess(left) && m.canAccess(right) }
                .filter { m -> m.parameterCount == 0 }
                .filter { m ->
                    m.name.toLowerCase() in setOf(
                        "get${ann.toLowerCase()}",
                        "is${ann.toLowerCase()}",
                        "has${ann.toLowerCase()}"
                    )
                }
            return getters.isNotEmpty() && getters.any { getter ->
                this(getter.invoke(left), getter.invoke(right))
            }
        }

        private fun processField(left: Any, right: Any, field: Field): Boolean {
            return (field.declaredAnnotations.filterIsInstance<XmlElement>().map { ann -> ann.name }.asSequence() +
                    field.declaredAnnotations.filterIsInstance<XmlAttribute>().map { ann -> ann.name }.asSequence())
                .all { ann -> processAnn(left, right, ann) }
        }

        operator fun invoke(_left: Any?, _right: Any?): Boolean {
            if (_left == null || _right == null)
                return _left == null && _right == null
            var left: Any = _left
            var right: Any = _right
            if (left is JAXBElement<*>)
                left = left.value
            if (right is JAXBElement<*>)
                right = right.value
            if (left is Iterable<*> && right is Iterable<*>) {
                return (left zip right).all { (l, r) -> this(l, r) }
            } else if (left.javaClass.packageName != TDefinitions::class.java.packageName || right.javaClass.packageName != TDefinitions::class.java.packageName) {
                return left == right
            } else if (left::class == right::class) {
                if (Pair(left, right) in seen || Pair(right, left) in seen) {
                    return true
                } else {
                    seen.add(Pair(left, right))
                    return left.javaClass.declaredFields
                        .all { field -> processField(left, right, field) }
                }
            } else {
                return false
            }
        }
    }

    @TestFactory
    fun loadAndSave(): Iterable<DynamicTest> {
        return strictFiles
            .map {
                DynamicTest.dynamicTest(it.path.replace(base, "")) {
                    val out = ByteArrayOutputStream()
                    val out2 = ByteArrayOutputStream()
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
}