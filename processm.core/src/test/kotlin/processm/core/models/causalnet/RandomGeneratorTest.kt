package processm.core.models.causalnet

import org.junit.jupiter.api.DynamicContainer
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestFactory
import processm.core.verifiers.CausalNetVerifier
import kotlin.random.Random
import kotlin.test.assertTrue

class RandomGeneratorTest {

    @TestFactory
    fun factory(): List<DynamicContainer> {
        return listOf(5, 7, 9).map { nNodes ->
            DynamicContainer.dynamicContainer(
                "nNodes=$nNodes",
                List(1000) {
                    DynamicTest.dynamicTest("seed=$it") {
                        val model = RandomGenerator(Random(it), nNodes = nNodes).generate()
                        println(model)
                        val v = CausalNetVerifier().verify(model)
                        assertTrue { v.validLoopFreeSequences.any() }
                        assertTrue { v.isSound }
                    }
                })
        }
    }

}