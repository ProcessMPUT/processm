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
            val cache = HashSet<String>()
            val tests = DynamicContainer.dynamicContainer(
                "nNodes=$nNodes",
                List(1000) {
                    val model = RandomGenerator(Random(it), nNodes = nNodes).generate()
                    with(model.toString()) {
                        if (this in cache)
                            return@List null
                        cache.add(this)
                    }
                    DynamicTest.dynamicTest("seed=$it") {
                        val v = CausalNetVerifier().verify(model)
                        assertTrue { v.validLoopFreeSequences.any() }
                        assertTrue { v.isSound }
                    }
                }.filterNotNull()
            )
            println("Running ${cache.size} RandomGeneratorTests for nNodes=$nNodes")
            tests
        }
    }

}