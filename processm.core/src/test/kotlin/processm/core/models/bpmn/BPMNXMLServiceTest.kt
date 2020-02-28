package processm.core.models.bpmn

import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestFactory
import java.io.File

class BPMNXMLServiceTest {
    @TestFactory
    fun generateTests(): Iterable<DynamicTest> {
        val base = "src/test/resources/bpmn-miwg-test-suite"
        return File(base)
            .walk()
            .filter { it.extension.toLowerCase() == "bpmn" }
            .map { DynamicTest.dynamicTest(it.path.replace(base, "")) { BPMNXMLService.load(it.inputStream()) } }
            .toList()
    }
}