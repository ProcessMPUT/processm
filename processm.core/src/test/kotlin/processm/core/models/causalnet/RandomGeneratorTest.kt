package processm.core.models.causalnet

import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestFactory
import processm.core.verifiers.CausalNetVerifier
import kotlin.random.Random
import kotlin.test.assertTrue

class RandomGeneratorTest {

    @TestFactory
    fun factory(): List<DynamicTest> {
        return List(1000) {
            DynamicTest.dynamicTest(it.toString()) {
                val seqs = CausalNetVerifier().verify(RandomGenerator(Random(it)).generate()).validLoopFreeSequences
                assertTrue { seqs.any() }
            }
        }
    }

}