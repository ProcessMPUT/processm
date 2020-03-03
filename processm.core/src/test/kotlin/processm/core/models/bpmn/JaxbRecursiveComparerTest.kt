package processm.core.models.bpmn

import org.junit.jupiter.api.Test
import processm.core.models.bpmn.jaxb.*
import java.io.File
import javax.xml.bind.JAXBElement
import javax.xml.namespace.QName
import kotlin.test.Ignore
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class JaxbRecursiveComparerTest {
    @Test
    fun differentId() {
        val a = TProperty()
        a.id = "a"
        val b = TProperty()
        b.id = "b"
        assertFalse(JaxbRecursiveComparer()(a, b))
    }

    @Test
    fun sameId() {
        val a = TProperty()
        a.id = "a"
        val b = TProperty()
        b.id = "a"
        assertTrue(JaxbRecursiveComparer()(a, b))
    }

    @Test
    fun differentName() {
        val a = TProperty()
        a.name = "a"
        val b = TProperty()
        b.name = "b"
        assertFalse(JaxbRecursiveComparer()(a, b))
    }

    @Test
    fun sameName() {
        val a = TProperty()
        a.name = "a"
        val b = TProperty()
        b.name = "a"
        assertTrue(JaxbRecursiveComparer()(a, b))
    }

    @Test
    fun sameDataState() {
        val a = TProperty()
        a.dataState = TDataState()
        val b = TProperty()
        b.dataState = TDataState()
        assertTrue(JaxbRecursiveComparer()(a, b))
    }

    @Test
    fun differentDataState() {
        val a = TProperty()
        a.dataState = TDataState()
        a.dataState.name = "ads"
        val b = TProperty()
        b.dataState = TDataState()
        assertFalse(JaxbRecursiveComparer()(a, b))
    }

    @Test
    fun sameOtherAttrs() {
        val a = TProperty()
        a.otherAttributes[QName("a")] = "1"
        a.otherAttributes[QName("b")] = "2"
        val b = TProperty()
        b.otherAttributes[QName("a")] = "1"
        b.otherAttributes[QName("b")] = "2"
        assertTrue(JaxbRecursiveComparer()(a, b))
    }

    @Test
    fun sameOtherAttrsDifferentOrder() {
        val a = TProperty()
        a.otherAttributes[QName("a")] = "1"
        a.otherAttributes[QName("b")] = "2"
        val b = TProperty()
        b.otherAttributes[QName("b")] = "2"
        b.otherAttributes[QName("a")] = "1"
        assertTrue(JaxbRecursiveComparer()(a, b))
    }

    @Test
    fun differentOtherAttrs() {
        val a = TProperty()
        a.otherAttributes[QName("a")] = "1"
        a.otherAttributes[QName("b")] = "2"
        val b = TProperty()
        b.otherAttributes[QName("a")] = "2"
        b.otherAttributes[QName("b")] = "2"
        assertFalse(JaxbRecursiveComparer()(a, b))
    }

    @Test
    fun sameListOrder() {
        val p1 = TProperty()
        p1.name = "p1"
        val p2 = TProperty()
        p2.name = "p2"
        val a = TStartEvent()
        a.property.addAll(listOf(p1, p2))
        val b = TStartEvent()
        b.property.addAll(listOf(p1, p2))
        assertTrue(JaxbRecursiveComparer()(a, b))
    }

    @Test
    fun differentListOrder() {
        val p1 = TProperty()
        p1.name = "p1"
        val p2 = TProperty()
        p2.name = "p2"
        val a = TStartEvent()
        a.property.addAll(listOf(p1, p2))
        val b = TStartEvent()
        b.property.addAll(listOf(p2, p1))
        assertFalse(JaxbRecursiveComparer()(a, b))
    }

    @Test
    fun sameDouble() {
        assertTrue(JaxbRecursiveComparer()(.1, .1))
    }

    @Ignore("Not implemented, as currently not necessary")
    @Test
    fun similarDouble() {
        assertTrue(JaxbRecursiveComparer()(.1, .1 + 1e-10))
    }

    @Test
    fun differentDouble() {
        assertFalse(JaxbRecursiveComparer()(.1, .2))
    }

    @Test
    fun sameAnyElement() {
        val p1 = TProperty()
        p1.name = "p1"
        val p2 = TProperty()
        p2.name = "p2"
        val a = TDocumentation()
        a.content.addAll(listOf(p1, p2))
        val b = TDocumentation()
        b.content.addAll(listOf(p1, p2))
        assertTrue(JaxbRecursiveComparer()(a, b))
    }


    @Test
    fun differentAnyElement() {
        val p1 = TProperty()
        p1.name = "p1"
        val p2 = TProperty()
        p2.name = "p2"
        val p3 = TProperty()
        p3.name = "p3"
        val a = TDocumentation()
        a.content.addAll(listOf(p1, p2))
        val b = TDocumentation()
        b.content.addAll(listOf(p1, p3))
        assertFalse(JaxbRecursiveComparer()(a, b))
    }

    @Test
    fun sameElementRef() {
        val p1 = TTask()
        p1.name = "p1"
        val p2 = TTask()
        p2.name = "p2"
        val a = TSubProcess()
        a.flowElement.addAll(
            listOf(
                JAXBElement(QName("p1"), p1.javaClass, p1),
                JAXBElement(QName("p2"), p2.javaClass, p2)
            )
        )
        val b = TSubProcess()
        b.flowElement.addAll(
            listOf(
                JAXBElement(QName("p1"), p1.javaClass, p1),
                JAXBElement(QName("p2"), p2.javaClass, p2)
            )
        )
        assertTrue(JaxbRecursiveComparer()(a, b))
    }

    @Test
    fun differentElementRef() {
        val p1 = TTask()
        p1.name = "p1"
        val p2 = TTask()
        p2.name = "p2"
        val p3 = TTask()
        p3.name = "p3"
        val a = TSubProcess()
        a.flowElement.addAll(
            listOf(
                JAXBElement(QName("p1"), p1.javaClass, p1),
                JAXBElement(QName("p2"), p2.javaClass, p2)
            )
        )
        val b = TSubProcess()
        b.flowElement.addAll(
            listOf(
                JAXBElement(QName("p1"), p1.javaClass, p1),
                JAXBElement(QName("p3"), p3.javaClass, p3)
            )
        )
        assertFalse(JaxbRecursiveComparer()(a, b))
    }

    @Test
    fun sameXmlElementRefs() {
        val p1 = TTask()
        p1.name = "p1"
        val p2 = TTask()
        p2.name = "p2"
        val a = TExpression()
        a.content.addAll(
            listOf(
                JAXBElement(QName("p1"), p1.javaClass, p1),
                JAXBElement(QName("p2"), p2.javaClass, p2)
            )
        )
        val b = TExpression()
        b.content.addAll(
            listOf(
                JAXBElement(QName("p1"), p1.javaClass, p1),
                JAXBElement(QName("p2"), p2.javaClass, p2)
            )
        )
        assertTrue(JaxbRecursiveComparer()(a, b))
    }


    @Test
    fun differentXmlElementRefs() {
        val p1 = TTask()
        p1.name = "p1"
        val p2 = TTask()
        p2.name = "p2"
        val p3 = TTask()
        p3.name = "p3"
        val a = TExpression()
        a.content.addAll(
            listOf(
                JAXBElement(QName("p1"), p1.javaClass, p1),
                JAXBElement(QName("p2"), p2.javaClass, p2)
            )
        )
        val b = TExpression()
        b.content.addAll(
            listOf(
                JAXBElement(QName("p1"), p1.javaClass, p1),
                JAXBElement(QName("p3"), p3.javaClass, p3)
            )
        )
        assertFalse(JaxbRecursiveComparer()(a, b))
    }

    @Test
    fun sameObjectFromFile() {
        val a =
            BPMNXMLService.loadStrict(File("src/test/resources/bpmn-miwg-test-suite/bpmn.io (Cawemo, Camunda Modeler) 1.12.0/A.1.0-export.bpmn").inputStream())
        assertTrue(JaxbRecursiveComparer()(a, a))
    }

    @Test
    fun sameFile() {
        val a =
            BPMNXMLService.loadStrict(File("src/test/resources/bpmn-miwg-test-suite/bpmn.io (Cawemo, Camunda Modeler) 1.12.0/A.1.0-export.bpmn").inputStream())
        val b =
            BPMNXMLService.loadStrict(File("src/test/resources/bpmn-miwg-test-suite/bpmn.io (Cawemo, Camunda Modeler) 1.12.0/A.1.0-export.bpmn").inputStream())
        assertTrue(JaxbRecursiveComparer()(a, b))
    }

    @Test
    fun differentFiles() {
        val a =
            BPMNXMLService.loadStrict(File("src/test/resources/bpmn-miwg-test-suite/bpmn.io (Cawemo, Camunda Modeler) 1.12.0/A.1.0-export.bpmn").inputStream())
        val b =
            BPMNXMLService.loadStrict(File("src/test/resources/bpmn-miwg-test-suite/bpmn.io (Cawemo, Camunda Modeler) 1.12.0/A.2.0-export.bpmn").inputStream())
        assertFalse(JaxbRecursiveComparer()(a, b))
    }

    @Test
    fun sameImportInTDefinitions() {
        val i1 = TImport()
        i1.namespace = "i1"
        val a = TDefinitions()
        a.import.add(i1)
        val b = TDefinitions()
        b.import.add(i1)
        assertTrue(JaxbRecursiveComparer()(a, b))
    }

    @Test
    fun differentImportInTDefinitions() {
        val i1 = TImport()
        i1.namespace = "i1"
        val i2 = TImport()
        i2.namespace = "i2"
        val a = TDefinitions()
        a.import.add(i1)
        val b = TDefinitions()
        b.import.add(i2)
        assertFalse(JaxbRecursiveComparer()(a, b))
    }

    @Test
    fun normalizeWhitespaces() {
        val cmp = JaxbRecursiveComparer()
        assertTrue(cmp("a\nb", "a b"))
        assertTrue(cmp("a  b", "a b"))
        assertFalse(cmp("a c", "a b"))
    }

    @Test
    fun sameOtherAttrsConsideringSpaceNormalization() {
        val a = TProperty()
        a.otherAttributes[QName("a")] = "1 2"
        a.otherAttributes[QName("b")] = "2\n1"
        val b = TProperty()
        b.otherAttributes[QName("a")] = "1 2"
        b.otherAttributes[QName("b")] = "2 1"
        assertTrue(JaxbRecursiveComparer()(a, b))
    }

}