package processm.core.models.causalnet

import org.junit.jupiter.api.DynamicContainer
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestFactory
import processm.core.verifiers.CausalNetVerifier
import processm.logging.logger
import kotlin.random.Random
import kotlin.system.measureTimeMillis
import kotlin.test.Ignore
import kotlin.test.assertTrue

class RandomGeneratorTest {

    companion object {
        val logger = logger()
    }

    @Ignore("Suspected to cause OOM on the build system.")
    @TestFactory
    fun factory(): List<DynamicContainer> {
        var out: List<DynamicContainer>? = null
        measureTimeMillis {
            out = listOf(5, 7, 9).map { nNodes ->
                val cache = HashSet<String>()
                val tests = DynamicContainer.dynamicContainer(
                    "nNodes=$nNodes",
                    List(1000) {
                        // TODO: Out of memory: Java heap space
                        if (it == 442 && nNodes == 9) return@List null

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
                logger.info("Running ${cache.size} RandomGeneratorTests for nNodes=$nNodes")
                tests
            }
        }.also { logger.info("RandomGeneratorTest run in: $it ms") }
        return out!!
    }

}
