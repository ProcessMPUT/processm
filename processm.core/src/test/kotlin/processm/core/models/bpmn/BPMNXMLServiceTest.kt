package processm.core.models.bpmn

import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestFactory
import java.io.File

class BPMNXMLServiceTest {
    @TestFactory
    fun generateTests(): Iterable<DynamicTest> {
        return File("src/test/resources/bpmn-miwg-test-suite")
            .walk()
            .filter { it.extension.toLowerCase() == "bpmn" }
            .map { DynamicTest.dynamicTest(it.name) { BPMNXMLService.load(it.inputStream()) } }
            .toList()
    }
}